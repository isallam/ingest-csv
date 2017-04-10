/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.objy.se.utils;

/**
 *
 * @author ibrahim
 */
public class CompositeKey implements TargetKey {
//-------------------------------------------------------------------------
  // the CompositeKey will be handled as a one unit to represnt the object
  // the combination of the keys will uniquely identify the object.
  //-------------------------------------------------------------------------
  protected SingleKey[] keys = null;

  public CompositeKey(SingleKey... singleKeys) {
    this.keys = singleKeys;
  }

  public String toString() {
    StringBuffer strBuffer = new StringBuffer();
    for (SingleKey key : keys)
    {
      strBuffer.append(key.toString() + "\n");
    }
    return strBuffer.toString();
  }
}
