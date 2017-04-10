/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.objy.se;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.objy.data.Attribute;
import com.objy.se.utils.CompositeKey;
import com.objy.se.utils.Relationship;
import com.objy.se.utils.SingleKey;
import com.objy.se.utils.TargetKey;
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
 *   ”Strings”: [{"SchemaName":“attribute”, "RawName": “name-of-column-in-source-data”}],
 *   “Integers”: [{"SchemaName":“attribute”, "RawName": “name-of-column-in-source-data”}],
 *   “Floats”: [{"SchemaName":“attribute”, "RawName": “name-of-column-in-source-data”}],
 *   “Dates”: [{"SchemaName":“attribute”, "RawName": “name-of-column-in-source-data”}],
 *   “Relationships”: 
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
  private static String StringsJSON = "Strings"; 
  private static String FloatsJSON = "Floats"; 
  private static String IntegersJSON = "Integers"; 
  private static String DatesJSON = "Dates"; 
  private static String RelationshipsJSON = "Relationships"; 
  private static String SchemaNameJSON = "SchemaName";
  private static String RawNameJSON = "RawName";
  private static String RelationshipNameJSON = "RelationshipName";
  private static String ToClassJSON = "ToClass";
  private static String ToClassRelationshipNameJSON = "ToClassRelationshipName";
  private static String KeyJSON = "Key";
  
  private String className;
  // map schema attribute names to raw data name
  protected HashMap<String, String> integerAttributeMap = new HashMap<>();
  protected HashMap<String, String> floatAttributeMap = new HashMap<>();
  protected HashMap<String, String> stringAttributeMap = new HashMap<>();
  protected HashMap<String, String> dateAttributeMap = new HashMap<>();
  
  protected List<Relationship> relationshipList = new ArrayList<>();
  
  private static final Logger LOG = LoggerFactory.getLogger(IngestMapper.class.getName());
  
  IngestMapper() {
    
  }

  IngestMapper(JsonObject json) {
    // construct needed information for processing data from the jsonObject
    className = json.get(ClassNameJSON).getAsString();
    
    if (json.has(StringsJSON)) {
      JsonArray jsonArray = json.get(StringsJSON).getAsJsonArray();
      processArray(jsonArray, stringAttributeMap);
    }
    
    if (json.has(IntegersJSON)) {
      JsonArray jsonArray = json.get(IntegersJSON).getAsJsonArray();
      processArray(jsonArray, integerAttributeMap);
    }
    
    if (json.has(FloatsJSON)) {
      JsonArray jsonArray = json.get(FloatsJSON).getAsJsonArray();
      processArray(jsonArray, floatAttributeMap);
    }

    if (json.has(DatesJSON)) {
      JsonArray jsonArray = json.get(DatesJSON).getAsJsonArray();
      processArray(jsonArray, dateAttributeMap);
    }
    
    if (json.has(RelationshipsJSON)) {
      JsonArray jsonArray = json.get(RelationshipsJSON).getAsJsonArray();
      processRelationships(jsonArray);
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

  
  private void processRelationships(JsonArray jsonArray) {

    for (JsonElement element : jsonArray) {
      JsonObject obj = (JsonObject) element;
      String relationshipName = obj.get(RelationshipNameJSON).getAsString();
      String toClass = obj.get(ToClassJSON).getAsString();
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
          Attribute attr = toClassAccessor.getAttribute(keySchemaName);
          if (attr == null)
          {
            LOG.error("Attribute: {} for toClass: {} is null", keySchemaName, toClass);
            throw new IllegalStateException("Invalid configuration... check mapper vs. schema");
          }
          SingleKey key = new SingleKey(keySchemaName, keyRawName, 
                  attr.getAttributeValueSpecification().getLogicalType());
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
        Attribute attr = toClassAccessor.getAttribute(keySchemaName);
        SingleKey key = new SingleKey(keySchemaName, keyRawName,
                  attr.getAttributeValueSpecification().getLogicalType());
        rel.add(key, relationshipName, toClassRelationshipName);
      }
      relationshipList.add(rel);
    }

  }
  
  protected HashMap<String, String> getStringsMap() {
    return this.stringAttributeMap;
  }

  protected HashMap<String, String>  getFloatMap() {
    return this.floatAttributeMap;
  }

  protected HashMap<String, String>  getIntegersMap() {
    return this.integerAttributeMap;
  }
  
  protected HashMap<String, String>  getDatesMap() {
    return this.dateAttributeMap;
  }

  protected List<Relationship> getRelationshipList() {
    return relationshipList;
  }
  
}
