/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */

package org.terracotta.modules.ehcache.hibernate.nontransactional;

import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class HibernateUtilNonEternal {

  private static SessionFactory      sessionFactory;
  private static final Configuration config;

  static {
    config = new Configuration().configure("/hibernate-config/hibernate-non-eternal.cfg.xml");
  }

  public synchronized static SessionFactory getSessionFactory() {
    if (sessionFactory == null) {
      try {
        sessionFactory = config.buildSessionFactory();
      } catch (HibernateException ex) {
        System.err.println("Initial SessionFactory creation failed." + ex);
        throw new ExceptionInInitializerError(ex);
      }
    }
    return sessionFactory;
  }

  public synchronized static void dropAndCreateDatabaseSchema() {
    config.setProperty("hibernate.hbm2ddl.auto", "create");
  }

}
