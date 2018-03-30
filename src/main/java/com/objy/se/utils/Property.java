/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.objy.se.utils;

import com.objy.data.Variable;

/**
 * Class to store information about and attribute name and value.
 *
 * @author ibrahim
 */
public class Property {
  String attrName;
  Object attrValue;

  Property(String name, Object value) {
    attrName = name;
    attrValue = value;
  }
  
  public String getName() {
    return attrName;
  }
  
  public Object getValue() {
    return attrValue;
  }
}
