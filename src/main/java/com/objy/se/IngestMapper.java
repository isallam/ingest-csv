/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.objy.se;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.objy.se.utils.CompositeKey;
import com.objy.se.utils.Relationship;
import com.objy.se.utils.SingleKey;
import com.objy.se.utils.TargetKey;
import com.objy.se.utils.TargetList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ibrahim
 */

/** 
 * Example of JSON used for managing the mapping between attributes of a class
 * and all it's relationships.
 * 
 * { 
 *   "ClassName": "name-of-class",
 *   "Attributes": [{"SchemaName":“attribute”, "RawName": “name-of-column-in-source-data”}],
 *   "Relationships": 
 *   [
 *     {
 *       “RelationshipName”: “relationship-attribute”,
 *       “ToClass”: “to-class-name”,
 *       "ToClassRelationshipName" : "to-class-relationship-name",
 *       “Key”: 
 *       [
 *         {
 *           “SchemaName”:”name-of-the-to-class-attribute”, 
 *           “RawName”:”name-of-the-column-in-source-data”
 *         }
 *       ]
 *     }
 *   ]
 * }
 * 
 * 
 */

class IngestMapper {
  private static String ClassNameJSON = "ClassName"; 
  private static String ClassKeyJSON = "ClassKey";
  private static String AttributesJSON = "Attributes"; 
  private static String RelationshipsJSON = "Relationships"; 
  private static String SchemaNameJSON = "SchemaName";
  private static String RawNameJSON = "RawName";
  private static String RelationshipNameJSON = "RelationshipName";
  private static String ToClassJSON = "ToClass";
  private static String ToClassRelationshipNameJSON = "ToClassRelationshipName";
  private static String KeyJSON = "Key";
  private static String DateFormatJSON = "DateFormat";
  private static String DateTimeFormatJSON = "DateTimeFormat";
  private static String TimeFormatJSON = "TimeFormat";
  
  private String className;
  private TargetKey classKey = null;
  private TargetList classTargetList = null;
  
  // map schema attribute names to raw data column name
  protected HashMap<String, String> attributesMap = new HashMap<>();
  
  protected List<Relationship> relationshipList = new ArrayList<>();
  
  private static final Logger LOG = LoggerFactory.getLogger(IngestMapper.class.getName());
  private String dateFormat = "dd-MM-yyyy";
  private String datetimeFormat = "yyyy-MM-dd'T'HH:mm:ss.n";
  private String timeFormat = "HH:mm:ss.n";
  
  IngestMapper() {
    
  }

  IngestMapper(JsonObject json) {
    // construct needed information for processing data from the jsonObject
    className = json.get(ClassNameJSON).getAsString();
    
    if (json.has(ClassKeyJSON))
    {
      JsonArray jsonArray = json.get(ClassKeyJSON).getAsJsonArray();
      processClassKey(jsonArray);
    }
    
    if (json.has(AttributesJSON)) {
      JsonArray jsonArray = json.get(AttributesJSON).getAsJsonArray();
      processArray(jsonArray, attributesMap);
    }
        
    if (json.has(RelationshipsJSON)) {
      JsonArray jsonArray = json.get(RelationshipsJSON).getAsJsonArray();
      processRelationships(jsonArray);
    }
    
    if (json.has(DateFormatJSON)) {
      dateFormat = json.get(DateFormatJSON).getAsString();
      LOG.info(">> DateFormat: {}", dateFormat);
    }

    if (json.has(DateTimeFormatJSON)) {
      datetimeFormat = json.get(DateTimeFormatJSON).getAsString();
      LOG.info(">> DateTimeFormat: {}", datetimeFormat);
    }

    if (json.has(TimeFormatJSON)) {
      timeFormat = json.get(TimeFormatJSON).getAsString();
      LOG.info(">> TimeFormat: {}", timeFormat);
    }
  }
  
  public String getClassName() {
    return className;
  }

/**
 * 
 * @param jsonArray
 * @param stringAttributeMap 
 */
  private void processArray(JsonArray jsonArray, HashMap<String, String> stringAttributeMap) {

    for (JsonElement element : jsonArray) {
      JsonObject obj = (JsonObject) element;
      String schemaName = obj.get(SchemaNameJSON).getAsString();
      String rawName = obj.get(RawNameJSON).getAsString();
      stringAttributeMap.put(schemaName, rawName);
    }
    
  }

  private void processClassKey(JsonArray jsonArray) {

      ClassAccessor classAccessor = SchemaManager.getInstance().getClassProxy(className);
      ArrayList<JsonObject> keys = new ArrayList<>();

      for (JsonElement keyElement : jsonArray) {
        JsonObject keyObj = (JsonObject) keyElement;
        keys.add(keyObj);
      }
      
      if (keys.size() > 1) // composite key
      {
        ArrayList<SingleKey> singleKeys = new ArrayList<>();
        for (JsonObject keyObj : keys) {
          String keySchemaName = keyObj.get(SchemaNameJSON).getAsString();
          String keyRawName = keyObj.get(RawNameJSON).getAsString();
          // get the type of the keySchemaName 
          ClassAccessor.AttributeInfo attrInfo = classAccessor.getAttribute(keySchemaName);
          SingleKey key = new SingleKey(keySchemaName, keyRawName, 
                  attrInfo.logicalType());
          singleKeys.add(key);
        }
        classKey = new CompositeKey(singleKeys.toArray(new SingleKey[0]));
      }
      else {
        JsonObject keyObj = keys.get(0);
        String keySchemaName = keyObj.get(SchemaNameJSON).getAsString();
        String keyRawName = keyObj.get(RawNameJSON).getAsString();
        // get the type of the keySchemaName 
        ClassAccessor.AttributeInfo attrInfo = classAccessor.getAttribute(keySchemaName);
        classKey = new SingleKey(keySchemaName, keyRawName,
                  attrInfo.logicalType());
      }
    }
  
  private void processRelationships(JsonArray jsonArray) {

    for (JsonElement element : jsonArray) {
      JsonObject obj = (JsonObject) element;
      String relationshipName = obj.get(RelationshipNameJSON).getAsString();
      //String toClass = obj.get(ToClassJSON).getAsString();
      JsonPrimitive prim = obj.getAsJsonPrimitive(ToClassJSON);
      String toClass = prim.getAsString();
      String toClassRelationshipName = null;
      if (obj.has(ToClassRelationshipNameJSON))
      {
        toClassRelationshipName = obj.get(ToClassRelationshipNameJSON).getAsString();
      }

      // configure relationship.
      Relationship rel = new Relationship(toClass);
      
      ClassAccessor toClassAccessor = SchemaManager.getInstance().getClassProxy(toClass);
      
      JsonArray keyArray = obj.get(KeyJSON).getAsJsonArray();
      ArrayList<JsonObject> keys = new ArrayList<>();

      for (JsonElement keyElement : keyArray) {
        JsonObject keyObj = (JsonObject) keyElement;
        keys.add(keyObj);
      }

      if (keys.size() > 1) // composite key
      {
        ArrayList<SingleKey> singleKeys = new ArrayList<>();
        for (JsonObject keyObj : keys) {
          String keySchemaName = keyObj.get(SchemaNameJSON).getAsString();
          String keyRawName = keyObj.get(RawNameJSON).getAsString();
          // get the type of the keySchemaName 
          ClassAccessor.AttributeInfo attrInfo = toClassAccessor.getAttribute(keySchemaName);
          SingleKey key = new SingleKey(keySchemaName, keyRawName, 
                  attrInfo.logicalType());
          singleKeys.add(key);
        }
        CompositeKey compositeKey = new CompositeKey(singleKeys.toArray(new SingleKey[0]));
        rel.add(compositeKey, relationshipName, toClassRelationshipName);
      }
      else {
        JsonObject keyObj = keys.get(0);
        String keySchemaName = keyObj.get(SchemaNameJSON).getAsString();
        String keyRawName = keyObj.get(RawNameJSON).getAsString();
        // get the type of the keySchemaName 
        ClassAccessor.AttributeInfo attrInfo = toClassAccessor.getAttribute(keySchemaName);
        SingleKey key = new SingleKey(keySchemaName, keyRawName,
                  attrInfo.logicalType());
        rel.add(key, relationshipName, toClassRelationshipName);
      }
      relationshipList.add(rel);
    }

  }
  
  protected HashMap<String, String> getAttributesMap() {
    return this.attributesMap;
  }


  protected List<Relationship> getRelationshipList() {
    return relationshipList;
  }


  public boolean hasRelationships() {
    return (!getRelationshipList().isEmpty());
  }
  
  public boolean hasClassKey() {
    return classKey != null;
  }
  
  public TargetList getClassTargetList() { 
    if (classTargetList == null) {
      // initialize TargetList
      classTargetList = new TargetList(
              SchemaManager.getInstance().getClassProxy(className), 
              classKey);
    }
    return classTargetList; 
  }

  TargetKey getClassKey() {
    return classKey;
  }

  String getDateFormat() {
    return dateFormat;
  }

  String getDateTimeFormat() {
    return datetimeFormat;
  }

  String getTimeFormat() {
    return timeFormat;
  }
  

}
