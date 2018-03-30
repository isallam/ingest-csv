/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.objy.se.utils;

import com.objy.se.ClassAccessor;
import com.objy.se.SchemaManager;

/**
 *
 * @author ibrahim
 */
public class RelationshipRef {
  protected TargetKey key;
  protected String refAttrName;
  protected String revRefAttrName = null;
  protected String revRefClassName = null;
  protected ClassAccessor revRefClassProxy = null;

  public RelationshipRef(TargetKey key, String refAttrName, 
          String revRefAttrName, String revRefClassName) {
    this.key = key;
    this.refAttrName = refAttrName;
    this.revRefAttrName = revRefAttrName;
    this.revRefClassName = revRefClassName;
  }

  public TargetKey getKey() { return key; }
  public String getRefAttrName() { return refAttrName; }
  public String getRevRefAttrName() { return revRefAttrName; }

  public ClassAccessor getRevRefClassProxy() {
    if (revRefClassProxy == null)
      revRefClassProxy = SchemaManager.getInstance().getClassProxy(revRefClassName);
    return revRefClassProxy;
  }
}
