/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.objy.se.utils;

import com.objy.data.Instance;
import com.objy.data.Variable;
import com.objy.targetFinder.ObjectTarget;
import com.objy.targetFinder.ObjectTargetKey;
import com.objy.targetFinder.ObjectTargetKeyBuilder;
import com.objy.targetFinder.TargetFinder;
import com.objy.se.ClassAccessor;
import java.util.ArrayList;
import org.apache.commons.csv.CSVRecord;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ibrahim
 */
public class TargetList {
   
  /******
   * Helper class to hold the target object reference for further processing.
   */
  private class TargetInfo {
    protected Property[] nameValues = null;
    protected ObjectTarget targetObject;

    protected TargetInfo(Property... nameValues) {
      this.nameValues = nameValues;
    }
  }
  
  
  //---------------------
  // local attributes...
  //---------------------
  private HashMap<Long, TargetInfo> targetInfoMap = new HashMap<>();
  protected ClassAccessor targetClass = null;
  protected TargetKey[] targetKeys = null;
  
  private static final Logger LOG = LoggerFactory.getLogger(TargetList.class.getName());
  
  public TargetList(ClassAccessor targetClass, TargetKey... targetKeys) {
    this.targetClass = targetClass;
    this.targetKeys = targetKeys;
  }
  
  public void collectTargetInfo(CSVRecord record) {
    for (TargetKey key : targetKeys) {
      //System.out.println(" >> in collectTargetInfo() - key:" + key);
      try {
        if (key instanceof SingleKey) {
          addToTargetInfoMap(record, (SingleKey)key);
        } else { // it's a compositeKey.
          addToTargetInfoMap(record, ((CompositeKey)key).keys);
        }
      } catch (Exception ex) {
        LOG.error("Error for keywords: {}", key.toString()); 
        //ex.printStackTrace();
        throw ex;
      }
    }
  }

  private void addToTargetInfoMap(CSVRecord record, 
                        SingleKey... singleKeywords) {
    Property[] nameValues = new Property[singleKeywords.length];
    for (int i = 0; i < singleKeywords.length; i++) {
      nameValues[i] = new Property(
            singleKeywords[i].attrName, 
            singleKeywords[i].getCorrectValue(record));
    }
    addToTargetInfoMap(nameValues);
  }  

  private void addToTargetInfoMap(Property... nameValues) {

    TargetInfo idInfo = new TargetList.TargetInfo(nameValues);
    long hashValue = hashOfValues(nameValues);
    targetInfoMap.put(hashValue, idInfo);
  }

  
  public Instance getTargetObject(CSVRecord record, TargetKey key) {
    Instance instance = null;
    try {
      if (key instanceof SingleKey) {
        instance = getTargetObjectForKyes(record, (SingleKey)key);
      } else { // it's a composite keyword.
        instance = getTargetObjectForKyes(record, ((CompositeKey)key).keys);
      }
    } catch (Exception ex) {
      LOG.error("Error for key(s): {}", key.toString()); 
      //ex.printStackTrace();
      throw ex;
    }
    
    return instance;
  }

  private Instance getTargetObjectForKyes(CSVRecord record, 
                        SingleKey... keys) {
//    Object[] values = new Property[keys.length];
    List<Object> values = new ArrayList<>();
    for (SingleKey key : keys) {
      //values[i] = record.get(key.rawFileAttrName);
      values.add(key.getCorrectValue(record));
    }
    return getTargetObject(values.toArray());
  }  
  
  private static long hashOfValues(Property...  nameValues) {
    String value = "";
    for (Property nameValue : nameValues) {
      value += nameValue.attrValue.toString();
    }
    return value.hashCode();
  }
  
  private static long hash(Object...  values) {
    String value = "";
    for (Object obj : values) {
      value += obj.toString();
    }
    return value.hashCode();
  }

  private Instance getTargetObject(Object... values)
  {
    long hashValue = hash(values);
    Instance instance = null;
    
    TargetInfo targetInfo = targetInfoMap.get(hashValue);
    if (targetInfo != null) 
      instance = targetInfo.targetObject.getInstance();
    
    if (instance == null)
      LOG.info("Ivalid instance for values: {}", values);

    return instance;
  }
  
  public void fetchTargets() {
    TargetFinder targetFinder = new TargetFinder();
    
    ObjectTargetKeyBuilder targetKeyBuilder;
    ObjectTargetKey targetKey;
    com.objy.data.Class objyClass = targetClass.getObjyClass();
    for (TargetInfo targetInfo : targetInfoMap.values()) {
      targetKeyBuilder = new ObjectTargetKeyBuilder(objyClass);
      for (Property keyValuePair : targetInfo.nameValues)
      {
//        System.out.println("Add to targetKeyBuilder: " + keyValuePair.attrName +
//                ", val: " + keyValuePair.attrValue);
        targetKeyBuilder.add(keyValuePair.attrName, new Variable(keyValuePair.attrValue));
      }
      targetKey = targetKeyBuilder.build();
      targetInfo.targetObject = targetFinder.getObjectTarget(targetKey);
    }
    targetFinder.resolveTargets();
  }
  
  public int createMissingTargets() {
    int count = 0;
    // iterate over the objectTargets and create data as needed.
    for (TargetInfo targetInfo : targetInfoMap.values()) {
      if (targetInfo.targetObject.getInstance() == null) 
      {
        // create the ID object
        Instance idInstance = targetClass.createObject(targetInfo.nameValues);
        targetInfo.targetObject.setInstance(idInstance);
        count++;
      }
    }
    return count;
  }

}
