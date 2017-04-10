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
import com.google.gson.stream.JsonReader; 
import com.objy.se.utils.Relationship;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IngestCSV {

  private static final Logger LOG = LoggerFactory.getLogger(IngestCSV.class.getName());

  private static class _Params {

    @Parameter(names = "-bootfile", description = "Name of the graph DB bootfile.")
    public String bootFile = null;
    @Parameter(names = "-csvfile", description = "Name of the CSV file to ingest.")
    public String csvFile = null;
    @Parameter(names = "-mapper", description = "Name of the JSON mapper file.")
    public String mapperFile = null;
  };

  private static _Params _params = new _Params();

  private void ingest(String csvFile, String mapperFile) {
    JsonElement jsonMapper = null;
    // read the mapper file as JSON and convert it to a Mapper object
    try {
      Gson gson = new Gson();
      JsonReader reader = new JsonReader(new FileReader(mapperFile));
      JsonParser parser = new JsonParser();
      jsonMapper = parser.parse(reader);
    } catch (FileNotFoundException ex) {
      LOG.error(ex.getMessage());
    }
    
    try {
      Transaction tx = new Transaction(TransactionMode.READ_UPDATE, "spark_write");
      // schemaManager need to be initialized within a transaction to cahce 
      // meta data information.
      String fileName = csvFile;

      if (fileName != null && jsonMapper != null) {
        if (jsonMapper instanceof JsonArray) {
          JsonArray jsonMapperArray = (JsonArray) jsonMapper;
          for (JsonElement jsonElement : jsonMapperArray)
          {
            IngestMapper mapper = new IngestMapper((JsonObject) jsonElement);
            int objCount = processFile(fileName, mapper);
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
            int objCount = processFile(fileName, mapper);
            // for now we read and process the whole file before commiting, but we
            // might change that to iterate and commit as needed.
            checkpoint(tx);
            LOG.info("Done {} Ingest... Total Objects: {}", fileName, objCount);
          
        }
      }

      Transaction.getCurrent().commit();
      Transaction.getCurrent().close();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void checkpoint(Transaction tx) {
    tx.commit();
    tx.start(TransactionMode.READ_UPDATE);
  }

  private int processFile(String fileName, IngestMapper mapper) {
    LOG.info("Starting Ingest for: {}: ", fileName);

    ClassAccessor classProxy = SchemaManager.getInstance().getClassProxy(mapper.getClassName());
    classProxy.setMapper(mapper);
    Iterable<CSVRecord> records = null;
    int objCount = 0;
    try {
      Reader in;
      // pass 1: process the ID and MSISDNsfrom the file and cache oids.
      in = new FileReader(fileName);
      // parse organization and find ID info.
      records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in);
      if (mapper.getRelationshipList().size() > 0) {
        for (CSVRecord record : records) {
          for (Relationship rel : mapper.getRelationshipList()) {
            rel.getTargetList().collectTargetInfo(record);
          }
        }
        // fetch the ids that exist in the FD.
        for (Relationship rel : mapper.getRelationshipList()) {
          rel.getTargetList().fetchTargets();
          int count = rel.getTargetList().createMissingTargets();
          LOG.info("created {} of {} objects", count, rel.toClassName());
        }
      }
      in.close();

      // pass 2:
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

    ingester.ingest(_params.csvFile, _params.mapperFile);

  }

  private static void processParams(String[] args) {
    System.out.println("args:" + args[0] + "," + args[1]);
    JCommander commander = new JCommander(_params, args);
    commander.setProgramName("IngestCSV");

    if (_params.bootFile == null) {
      System.err.println("You need to provide a graph DB bootfile path");
      commander.usage();
      System.exit(1);
    }
    if (_params.csvFile == null) {
      System.err.println("You must provide a CSV file to ingest");
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
