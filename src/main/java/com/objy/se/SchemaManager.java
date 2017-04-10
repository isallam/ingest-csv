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

import java.util.HashMap;

public class SchemaManager {

  public HashMap<String, ClassAccessor> classProxyMap = new HashMap<>();

  private static SchemaManager schemaManagerInstance = null;

  public static SchemaManager getInstance() {
    if (schemaManagerInstance == null) {
      schemaManagerInstance = new SchemaManager();
    }
    return schemaManagerInstance;
  }

  public ClassAccessor getClassProxy(String className) {
    ClassAccessor classAccessor = null;
    
    if (!classProxyMap.containsKey(className)) {
      classAccessor = new ClassAccessor(className);
      classAccessor.init();
      classProxyMap.put(className, classAccessor);
    }
    
    classAccessor = classProxyMap.get(className);
    return classAccessor;
  }

}
