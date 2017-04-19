/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.objy.se;

/**
 *
 * @author ibrahim
 */
import com.objy.data.Attribute;
import com.objy.data.Instance;
import com.objy.data.LogicalType;
import com.objy.data.Reference;
import com.objy.data.Variable;
import com.objy.se.utils.Property;
import com.objy.se.utils.Relationship;
import com.objy.se.utils.TargetKey;
import com.objy.se.utils.TargetList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVRecord;

/**
 *
 * @author ibrahim
 */
public class ClassAccessor {

  protected com.objy.data.Class classRef = null;
  protected String className = null;
  
  //protected static ArrayList<String> attributeList = new ArrayList<>();
  protected HashMap<String, Attribute> attributeMap = new HashMap<>();
  private IngestMapper mapper = null;
  
  public ClassAccessor(String className)
  {
    this.className = className;
  }
  
 
  public void init() {
    classRef = com.objy.data.Class.lookupClass(this.className);
    Iterator<Variable> attrItr = classRef.getAttributes().iterator();
    Attribute attr = null;
    while (attrItr.hasNext()) {
      attr = attrItr.next().attributeValue();
      //Attribute attr = classRef.lookupAttribute(attrName);
      attributeMap.put(attr.getName(), attr);
    }
  }

  public String getClassName() {
    return className;
  }
  

  public com.objy.data.Class getObjyClass() {
    return classRef;
  }
      
  public Attribute getAttribute(String attrName) {
    return attributeMap.get(attrName);
  }
  
  private Instance createInstance() {
      //objectCreatedCounter++;
      return Instance.createPersistent(classRef);
  }

  public Instance createObject(CSVRecord record)
  {
    
    Instance instance = null;
    
    // check if we already have the instance
    if (mapper.hasClassKey())
    {
      instance = mapper.getClassTargetList().getTargetObject(record, mapper.getClassKey());
    }
    
    if (instance == null) {
      instance = createInstance();
    }

    // iterate all relationships and resolve references
    for (Relationship rel : mapper.getRelationshipList())
    {
      TargetList relTarget = rel.getTargetList();
      for (Relationship.RelationshipRef relRef : rel.getRelationshipRefList())
      {
        Instance refInstance = relTarget.getTargetObject(record, relRef.getKey());
        if (refInstance != null)
          setReference(instance, relRef.getRefAttrName(), refInstance);
        if (relRef.getRevRefAttrName() != null) // we have a reverse attribute to set
        {
          relRef.getRevRefClassProxy().addReference(refInstance, relRef.getRevRefAttrName(), instance);
        }
      }
    }

    setAttributes(instance, record);
    
    return instance;
  }
  
  /**
   *  Create an instance based on the list of properties passed.
   *  This will do a partial creation of some objects.
   * 
   * @param properties
   * @return new created Instance
   */
  public Instance createObject(Property... properties) {
    Instance instance = createInstance();
    for (Property property : properties)
    {
      setAttributeValue(instance, property.getName(), property.getValue());
    }
    return instance;
  }
  
  public Instance setAttributes(Instance instance, CSVRecord record) {
    
  
  // iterate and create any Integer attribute
    for (Map.Entry<String, String> entry : mapper.getIntegersMap().entrySet())
    {
//      System.out.println("Entry()" + entry.toString());
      try {
        long attrValue = 0;
        String attrValueStr = record.get(entry.getValue());
        if (!attrValueStr.equals(""))
          attrValue = Long.parseLong(attrValueStr);
        setAttributeValue(instance, entry.getKey(), attrValue);
      } catch (NumberFormatException nfEx) {
//        System.out.println("... entry: " + entry.getValue() + " for raw: " + entry.getKey());
        nfEx.printStackTrace();
        throw nfEx;
      }
    }
    
    // iterate and create any Real atttribute
    for (Map.Entry<String, String> entry : mapper.getFloatMap().entrySet())
    {
//      System.out.println("Entry()" + entry.toString());
      try {
        double attrValue = Double.parseDouble(record.get(entry.getValue()));
        setAttributeValue(instance, entry.getKey(), attrValue);
      } catch (NumberFormatException nfEx) {
        nfEx.printStackTrace();
        throw nfEx;
      }
    }
    
    // iterate and create any string attribute
    for (Map.Entry<String, String> entry : mapper.getStringsMap().entrySet())
    {
//      System.out.println("Entry()" + entry.toString());
      setAttributeValue(instance, entry.getKey(), record.get(entry.getValue()));
    }
    
    return instance;
  }

  private void setAttributeValue(Instance instance, Attribute attribute, Object value) {
      Variable varValue = new Variable();
      instance.getAttributeValue(attribute, varValue);
      varValue.set(value);
  }

  public void setAttributeValue(Instance instance, String attributeName, Object value) {
    try {
      Attribute attribute = attributeMap.get(attributeName);
      setAttributeValue(instance, attribute, value);
    } catch (Exception ex) {
      System.out.println("Error for : " + instance.getClass(true).getName() +  
              " -attr: " + attributeName);
      ex.printStackTrace();
    }
  }
  
          
  private void setReference(Instance instance, Attribute attribute, Instance value) {
      Variable varValue = new Variable();
      instance.getAttributeValue(attribute, varValue);

      if (attribute.getAttributeValueSpecification().getLogicalType() == LogicalType.LIST) {
          varValue.listValue().add(new Variable(new Reference(value)));
      } else if (attribute.getAttributeValueSpecification().getLogicalType() == LogicalType.REFERENCE) {
          varValue.set(new Reference(value));
      } else {
          throw new IllegalArgumentException("Illegal attribute type "+ attribute.getAttributeValueSpecification().getLogicalType().name() +" for Instance value.");
      }
  }

  public void setReference(Instance instance, String attributeName, Instance value) {
      Attribute attribute = attributeMap.get(attributeName);
      if (instance == null || value == null || attribute == null)
        System.out.println("For attr: " + attributeName + " - instance/attribute/value: " + 
                instance + " / " + attribute + " / " + value );
      setReference(instance, attribute, value);
  }
  
  public void addReference(Instance instance, String attributeName, Instance value) {
      Attribute attribute = attributeMap.get(attributeName);
      setReference(instance, attribute, value);
  }

  void setMapper(IngestMapper mapper) {
    this.mapper = mapper;
  }
}

