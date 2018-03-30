package com.objy.se;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import com.objy.db.Connection;
import com.objy.db.Objy;
import com.objy.db.Transaction;
import com.objy.db.TransactionMode;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.objy.se.utils.Relationship;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IngestCSV {

  private static final Logger LOG = LoggerFactory.getLogger(IngestCSV.class.getName());

  private static class _Params {

    @Parameter(names = "-bootfile", description = "Name of the graph DB bootfile.")
    public String bootFile = null;
    @Parameter(names = "-csvfile", description = "Name of the CSV file to ingest.")
    public String csvFile = null;
    @Parameter(names = "-csvfilePattern", description = "Quoted Pattern of the CSV files to ingest.")
    public String csvfilePattern = null;
    @Parameter(names = "-mapper", description = "Name of the JSON mapper file.")
    public String mapperFile = null;
    @Parameter(names = "-isTabDelim", description = "File use Tab delimited fields")
    public Boolean isTabDelim = false;
    @Parameter(names = "-commitEvery", description = "Number of record ingested for each commit.")
    public Integer commitEvery = 20000;
  };

  private static _Params _params = new _Params();

  static String readFile(String filename, Charset encoding) 
    throws IOException 
  {
    byte[] encoded = Files.readAllBytes(java.nio.file.Paths.get(filename));
    return new String(encoded, encoding);
  }
  
  private void ingest(String csvFile, String mapperFile, int commitEvery, 
          boolean isTabDelim) {
    JsonElement jsonMapper = null;
    // read the mapper file as JSON and convert it to a Mapper object
    try {
      
      Gson gson = new Gson();
      String jsonString = readFile(mapperFile, StandardCharsets.UTF_8);
      //System.out.println("JSON String: " + jsonString);
      
      JsonParser parser = new JsonParser();
      jsonMapper = parser.parse(jsonString);
      
    } catch (FileNotFoundException ex) {
      LOG.error(ex.getMessage());
    } catch (IOException ioEx) {
      LOG.error(ioEx.getMessage());
    }     
    try {
      Transaction tx = new Transaction(TransactionMode.READ_UPDATE, "spark_write");
      long timeStart = System.currentTimeMillis();
      // schemaManager need to be initialized within a transaction to cahce 
      // meta data information.
      String fileName = csvFile;

      if (fileName != null && jsonMapper != null) {
        if (jsonMapper instanceof JsonArray) {
          JsonArray jsonMapperArray = (JsonArray) jsonMapper;
          for (JsonElement jsonElement : jsonMapperArray)
          {
            IngestMapper mapper = new IngestMapper((JsonObject) jsonElement);
            int objCount = processFile(fileName, mapper, isTabDelim);
            // for now we read and process the whole file before commiting, but we
            // might change that to iterate and commit as needed.
            checkpoint(tx);
            LOG.info("Done {} Ingest... Total Objects: {}", fileName, objCount);
          }   
        }
        else 
        {
            JsonObject jsonObject = (JsonObject) jsonMapper;
            IngestMapper mapper = new IngestMapper(jsonObject);
            int objCount = processFile(fileName, mapper, isTabDelim);
            // for now we read and process the whole file before commiting, but we
            // might change that to iterate and commit as needed.
            checkpoint(tx);
            LOG.info("Done {} Ingest... Total Objects: {}", fileName, objCount);
        }
      }

      Transaction.getCurrent().commit();
      Transaction.getCurrent().close();
      long timeDiff = System.currentTimeMillis() - timeStart;
      LOG.info("Time: {} sec", (timeDiff/1000.0));
      
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void checkpoint(Transaction tx) {
    tx.commit();
    tx.start(TransactionMode.READ_UPDATE);
  }

  private int processFile(String fileName, IngestMapper mapper, boolean isTabDelim) {
    //LOG.info("Starting Ingest for: {}: ", fileName);

    ClassAccessor classProxy = SchemaManager.getInstance().getClassProxy(mapper.getClassName());
    classProxy.setMapper(mapper);
    Iterable<CSVRecord> records = null;
    int objCount = 0;
    try {
      Reader in;
      // pass 1: process the keys and related types from the file and cache oids.
      in = new FileReader(fileName);
      // parse the file and pick the keys needed.
      if (isTabDelim) {
        records = CSVFormat.TDF.withFirstRecordAsHeader().parse(in);
      }
      else {
        records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in);
      }
      boolean doProcessForRelationships = mapper.hasRelationships();
      boolean doProcessClassKeys = mapper.hasClassKey();
      if (doProcessClassKeys || doProcessForRelationships) {
        for (CSVRecord record : records) {
          if (doProcessForRelationships) {
            for (Relationship rel : mapper.getRelationshipList()) {
              rel.getTargetList().collectTargetInfo(record);
            }
          }
          if (doProcessClassKeys) {
            mapper.getClassTargetList().collectTargetInfo(record);
          }
        }
        // fetch the ids that exist in the FD.
        if (doProcessForRelationships)
        {
          for (Relationship rel : mapper.getRelationshipList()) {
            rel.getTargetList().fetchTargets();
            int count = rel.getTargetList().createMissingTargets();
            LOG.info("created {} of {} objects", count, rel.toClassName());
          }
        }
        // fetch existing objects if available.
        if (doProcessClassKeys) {
          mapper.getClassTargetList().fetchTargets();
          int count = mapper.getClassTargetList().createMissingTargets();
          LOG.info("created {} of {} objects", count, mapper.getClassName());
        }
      }
      in.close();

      // pass 2:
      LOG.info("Phase 2: update newley created objects and connect related objects.");
      in = new FileReader(fileName);
      records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in);
      for (CSVRecord record : records) {
        classProxy.createObject(record);
        objCount++;
      }
    } catch (FileNotFoundException ex) {
      LOG.error(ex.getMessage());
    } catch (IOException ex) {
      LOG.error(ex.getMessage());
    }
    return objCount;
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    processParams(args);
    IngestCSV ingester = new IngestCSV();

    // some intialization for Thingspan.
    new Connection(_params.bootFile);
    Objy.enableConfiguration();

    if (_params.csvFile != null)
    {
      ingester.ingest(_params.csvFile, _params.mapperFile, 
              _params.commitEvery, _params.isTabDelim);
    }
    else if (_params.csvfilePattern != null)
    {
      System.out.println("processing files: " + _params.csvfilePattern);
      int count = 1;
      // get the path section from the pattern.
      int pathPartEnd = _params.csvfilePattern.lastIndexOf(File.separator);
      String pathString = _params.csvfilePattern.substring(0, pathPartEnd);
      String globString = _params.csvfilePattern.substring(pathPartEnd+1);
//      System.out.println("Path: " + pathString);
//      System.out.println("Glob: " + globString);
      try {
        Path dir = FileSystems.getDefault().getPath(pathString);
        DirectoryStream<Path> ds = Files.newDirectoryStream(dir, globString);
        List<String> files = new ArrayList<String>();
        for(Path path : ds) {
          files.add(path.toString());
        }
        int totalCount = files.size();
        for (String csvFile : files) {
          LOG.info("Processing File: {}", csvFile);
          ingester.ingest(csvFile, _params.mapperFile, 
                  _params.commitEvery, _params.isTabDelim);
          LOG.info("Processed {} of {}", count, totalCount);
          count++;
        }
      } catch (IOException x) {
        // IOException can never be thrown by the iteration.
        // In this snippet, it can // only be thrown by newDirectoryStream.
        System.err.println(x);
      }
    }
  }

  private static void processParams(String[] args) {
    //System.out.println("args:" + args[0] + "," + args[1]);
    JCommander commander = new JCommander(_params, args);
    commander.setProgramName("IngestCSV");

    if (_params.bootFile == null) {
      System.err.println("You need to provide a graph DB bootfile path");
      commander.usage();
      System.exit(1);
    }
    if (_params.csvFile == null && _params.csvfilePattern == null) {
      System.err.println("You must provide a CSV file or file pattern to ingest");
      commander.usage();
      System.exit(1);
    }
    if (_params.mapperFile == null) {
      System.err.println("You must provide a Mapper file to map raw data to schema");
      commander.usage();
      System.exit(1);

    }
  }
}
