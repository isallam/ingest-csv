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
import com.objy.data.List;
import com.objy.data.LogicalType;
import com.objy.data.Reference;
import com.objy.data.Variable;
import com.objy.se.utils.Property;
import com.objy.se.utils.Relationship;
import com.objy.se.utils.TargetList;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import org.apache.commons.csv.CSVRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ibrahim
 */
public class ClassAccessor {

  private static final Logger LOG = LoggerFactory.getLogger(ClassAccessor.class.getName());
  
  class AttributeInfo {
    private Attribute   _attribute;
    private LogicalType _logicalType;
    public AttributeInfo(Attribute attr, LogicalType logicalType) {
      _attribute = attr;
      _logicalType = logicalType;
    }
    public Attribute attribute() { return _attribute; }
    public LogicalType logicalType() { return _logicalType; }
  }
  
  protected com.objy.data.Class classRef = null;
  protected String className = null;
  
  protected HashMap<String, AttributeInfo> attributeMap = new HashMap<>();
  private IngestMapper mapper = null;
  private boolean isInitialized = false;
  
  public ClassAccessor(String className)
  {
    this.className = className;
  }
  
 
  private void init() {
    classRef = com.objy.data.Class.lookupClass(this.className);
    Iterator<Variable> attrItr = classRef.getAttributes().iterator();
    Attribute attr = null;
    while (attrItr.hasNext()) {
      attr = attrItr.next().attributeValue();
      //Attribute attr = classRef.lookupAttribute(attrName);
      AttributeInfo attributeInfo = new AttributeInfo(attr, 
                attr.getAttributeValueSpecification().getLogicalType());
      attributeMap.put(attr.getName(), attributeInfo);
    }
    isInitialized = true;
  }

  public String getClassName() {
    return className;
  }
  

  public com.objy.data.Class getObjyClass() {
    if (!isInitialized)
    {
      init();
    }
    return classRef;
  }
      
  public AttributeInfo getAttribute(String attrName) {
    if (!isInitialized)
      init();
    AttributeInfo attrInfo = attributeMap.get(attrName);
    if (attrInfo == null)
    {
      LOG.error("Attribute: {} for Class: {} is null", attrName, className);
      throw new IllegalStateException("Invalid configuration... check mapper vs. schema");
    }
    return attrInfo;
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
  
  
  
  public Variable getCorrectValue(String strValue, LogicalType logicalType) {
    Variable retValue = null;
    switch (logicalType) {
      case INTEGER: {
        long attrValue = 0;
        try {
          if (!strValue.equals("")) {
            attrValue = Long.parseLong(strValue);
          }
        } catch (NumberFormatException nfEx) {
//        System.out.println("... entry: " + entry.getValue() + " for raw: " + entry.getKey());
          nfEx.printStackTrace();
          throw nfEx;
        }
        retValue.set(attrValue);
      }
      break;
      case REAL: 
        {
          double attrValue = 0;
          try {
            if (!strValue.equals("")) {
              attrValue = Double.parseDouble(strValue);
            }
          } catch (NumberFormatException nfEx) {
  //        System.out.println("... entry: " + entry.getValue() + " for raw: " + entry.getKey());
            nfEx.printStackTrace();
            throw nfEx;
          }
          retValue.set(attrValue);
        }
        break;
      case STRING:
        retValue.set(strValue);
        break;
      case BOOLEAN:
        {
          if (strValue.equalsIgnoreCase("TRUE") || strValue.equals("1")) {
            retValue.set(true);
          }
          else if (strValue.equalsIgnoreCase("FALSE") || strValue.equals("0")) {
            retValue.set(false);
          }
          else {
            LOG.error("Expected Boolean value but got: {}", strValue);
            throw new IllegalStateException("Possible invalid configuration... check mapper vs. schema" +
                    "... or check records for invalid values");
          }
        }
        break;

      case CHARACTER:
        {
          if (strValue.length() == 1) {
            retValue.set(strValue.charAt(0));
          }
          else { /* not a char value... report that */
            LOG.error("Expected Character value but got: {}", strValue);
            throw new IllegalStateException("Possible invalid configuration... check mapper vs. schema" +
                    "... or check records for invalid values");
          }
        }
        break;
      case DATE:
        {
          try {
            Date date = DateFormat.getDateInstance().parse(strValue);
            retValue.set(date);
          } catch (ParseException ex) {
            LOG.error(ex.toString());
            throw new IllegalStateException("Possible invalid configuration... check mapper vs. schema" +
                     "... or check records for invalid values");
          }
        }
        break;
      case DATE_TIME:
        {
          try {
            Date date = DateFormat.getDateTimeInstance().parse(strValue);
            retValue.set(date);
          } catch (ParseException ex) {
            LOG.error(ex.toString());
            throw new IllegalStateException("Possible invalid configuration... check mapper vs. schema" +
                     "... or check records for invalid values");
          }
        }
        break;
      case TIME:
        {
          try {
            Date date = DateFormat.getTimeInstance().parse(strValue);
            retValue.set(date);
          } catch (ParseException ex) {
            LOG.error(ex.toString());
            throw new IllegalStateException("Possible invalid configuration... check mapper vs. schema" +
                     "... or check records for invalid values");
          }
        }
      default:
      {
        throw new UnsupportedOperationException("LogicalType: " + logicalType + " is not supported!!!");
      }
    }
    return retValue;
  }
    
  public Instance setAttributes(Instance instance, CSVRecord record) {
    
  
  // iterate and create attributes
    for (Map.Entry<String, String> entry : mapper.getAttributesMap().entrySet())
    {
//      System.out.println("Entry()" + entry.toString());
        String attrValue = record.get(entry.getValue());
        setAttributeValue(instance, entry.getKey(), attrValue);
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
      Attribute attribute = attributeMap.get(attributeName).attribute();
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
        List list = varValue.listValue();
        if (!doListContainReference(list, value))
          list.add(new Variable(new Reference(value)));
      } else if (attribute.getAttributeValueSpecification().getLogicalType() == LogicalType.REFERENCE) {
          varValue.set(new Reference(value));
      } else {
          throw new IllegalArgumentException("Illegal attribute type "+ attribute.getAttributeValueSpecification().getLogicalType().name() +" for Instance value.");
      }
  }

  public void setReference(Instance instance, String attributeName, Instance value) {
      Attribute attribute = attributeMap.get(attributeName).attribute();
      if (instance == null || value == null || attribute == null)
        System.out.println("For attr: " + attributeName + " - instance/attribute/value: " + 
                instance + " / " + attribute + " / " + value );
      setReference(instance, attribute, value);
  }
  
  public void addReference(Instance instance, String attributeName, Instance value) {
      Attribute attribute = attributeMap.get(attributeName).attribute();
      setReference(instance, attribute, value);
  }

  void setMapper(IngestMapper mapper) {
    this.mapper = mapper;
  }

  private boolean doListContainReference(List list, Instance value) {
    Variable var = new Variable();
    long valueOid = value.getObjectId().asLong();
    for (int i = 0; i < list.size(); i++)
    {
      list.get(i, var);
      long refOid = var.referenceValue().getObjectId().asLong();
      if (refOid == valueOid)
      {
        return true;
      }
    }
    
    return false;
  }
}

