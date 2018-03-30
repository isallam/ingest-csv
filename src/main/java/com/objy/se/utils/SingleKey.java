/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.objy.se.utils;

import com.objy.data.LogicalType;

/**
 *
 * @author ibrahim
 */
public class SingleKey implements TargetKey {

 //-------------------------------------------------------------------------
  // most keys are single, but it's possible to have and object that uses a 
  // composite key formed from two columns to define uniqueness
  //----------------------------------------------------------------------s---
    protected String attrName;
    protected String rawFileAttrName;
    protected LogicalType logicalType;
    
    public SingleKey(String attrName, String rawFileAttrName,
            LogicalType logicalType) {
      this.attrName = attrName;
      this.rawFileAttrName = rawFileAttrName;
      this.logicalType = logicalType;
    }

    public String toString() {
      return "attrName: " + attrName + ", rawFileName: " + rawFileAttrName +
              ", logicalType: " + logicalType.toString();
    }

//  Object getCorrectValue(CSVRecord record) {
//    if (logicalType == LogicalType.INTEGER) {
//      long attrValue = 0;
//      String attrValueStr = record.get(rawFileAttrName);
//      if (!attrValueStr.equals(""))
//        attrValue = Long.parseLong(attrValueStr);
//      return attrValue;
//    }
//    if (logicalType == LogicalType.REAL) {
//      double attrValue = 0;
//      String attrValueStr = record.get(rawFileAttrName);
//      if (!attrValueStr.equals(""))
//        attrValue = Double.parseDouble(attrValueStr);
//      return attrValue;
//    }
//    else 
//      return record.get(rawFileAttrName);
//  }
}
