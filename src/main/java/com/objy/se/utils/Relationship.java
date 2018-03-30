/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.objy.se.utils;

import com.objy.se.SchemaManager;
import com.objy.se.ClassAccessor;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author ibrahim
 */
public class Relationship {

  
  protected String toClassName = null;
  protected Boolean isToOne = true;
  protected TargetList targetList = null;
  protected List<RelationshipRef> relationshipRefList = new ArrayList<>();
  
  public Relationship(String toClassName) {
    this.toClassName = toClassName;
  }

  public Relationship(String toClassName, Boolean isToMany) {
    this.toClassName = toClassName;
    this.isToOne = !isToMany;
  }
  
  public String toClassName() { return this.toClassName; }
  
  public void add(TargetKey key, String refAttrName, 
          String toClassRelationshipName) {
    RelationshipRef relationshipRef = new RelationshipRef(key, refAttrName, 
            toClassRelationshipName, toClassName);
    relationshipRefList.add(relationshipRef);
  }
  
  public List<RelationshipRef> getRelationshipRefList() {
    return relationshipRefList;
  }
  
  public TargetList getTargetList() { 
    if (targetList == null) {
      // initialize TargetList
      targetList = new TargetList(
              SchemaManager.getInstance().getClassProxy(toClassName),
              getKeys());
    }
    return targetList; 
  }

  private TargetKey[] getKeys() {
    List<TargetKey> targetKeys = new ArrayList<>();
    relationshipRefList.forEach(relRef -> targetKeys.add(relRef.key));
    return targetKeys.toArray(new TargetKey[targetKeys.size()]);
  }
  
}
