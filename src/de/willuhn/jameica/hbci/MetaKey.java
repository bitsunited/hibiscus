/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.hbci;

import java.rmi.RemoteException;

import de.willuhn.jameica.hbci.rmi.HibiscusDBObject;

/**
 * Listet bekannte Meta-Keys, die zu Fachobjekten gespeichert werden koennen.
 */
public enum MetaKey
{
  @SuppressWarnings("javadoc") REMINDER_UUID("reminder.uuid",        null, null),
  @SuppressWarnings("javadoc") REMINDER_TEMPLATE("reminder.template",null, null),
  @SuppressWarnings("javadoc") REMINDER_CREATOR("reminder.creator",  null, "unknown"),
  @SuppressWarnings("javadoc") ADDRESS_ID("address.id",              null, null),
  
  /**
   * SEPA-Mandatsreferenz.
   */
  SEPA_MANDATE_ID("sepa.mandateid", "SEPA-Mandatsreferenz",null),
  
  /**
   * Signatur-Datum des Mandats.
   */
  SEPA_MANDATE_SIGDATE("sepa.sigdate", "Signatur-Datum des Mandats",null),
  
  /**
   * SEPA-Lastschrift Sequenz-Typ.
   */
  SEPA_SEQUENCE_CODE("sepa.sequencetype", "Signatur-Datum des Mandats",null),

  /**
   * Gl�ubiger-Identifikation.
   */
  SEPA_CREDITOR_ID("sepa.creditor.id", "Gl�ubiger-Identifikation",null),
  
  /**
   * Batch-Book-Flag.
   */
  SEPA_BATCHBOOK("sepa.batchbook", "Ums�tze dieses Sammelauftrages",null),

  ;

  private String name         = null;
  private String description  = null;
  private String defaultValue = null;
  
  /**
   * ct.
   * @param name
   * @param description
   * @param defaultValue
   */
  private MetaKey(String name, String description, String defaultValue)
  {
    this.name         = name;
    this.description  = description;
    this.defaultValue = defaultValue;
  }
  
  /**
   * Liefert den Namen des Meta-Keys.
   * @return der Name des Meta-Keys.
   */
  public String getName()
  {
    return this.name;
  }
  
  /**
   * Liefert einen optionalen Beschreibungstext zu dem Meta-Key.
   * @return optionaler Beschreibungstext zu dem Meta-Key.
   */
  public String getDescription()
  {
    return this.description;
  }
  
  /**
   * Liefert den Wert des Meta-Keys fuer das Objekt.
   * @param o das Objekt.
   * @return der Wert des Meta-Keys oder der Default-Wert, wenn kein Wert existiert.
   * @throws RemoteException
   */
  public String get(HibiscusDBObject o) throws RemoteException
  {
    return this.get(o,null);
  }
  
  /**
   * Speichert den Wert des Meta-Keys fuer das Objekt.
   * @param o das Objekt.
   * @param value der Wert des Meta-Keys.
   * @throws RemoteException
   */
  public void set(HibiscusDBObject o, String value) throws RemoteException
  {
    this.set(o,null,value);
  }
  
  /**
   * Liefert den Wert des Meta-Keys fuer das Objekt.
   * @param o das Objekt.
   * @param suffix optionaler Suffix, um verschiedene Varianten des Meta-Key verwenden zu koennen.
   * @return der Wert des Meta-Keys oder der Default-Wert, wenn kein Wert existiert.
   * @throws RemoteException
   */
  public String get(HibiscusDBObject o, String suffix) throws RemoteException
  {
    String key = this.name;
    if (suffix != null)
      key = key + "." + suffix;
    return o.getMeta(key,this.defaultValue);
  }
  
  /**
   * Speichert den Wert des Meta-Keys fuer das Objekt.
   * @param o das Objekt.
   * @param suffix optionaler Suffix, um verschiedene Varianten des Meta-Key verwenden zu koennen.
   * @param value der Wert des Meta-Keys.
   * @throws RemoteException
   */
  public void set(HibiscusDBObject o, String suffix, String value) throws RemoteException
  {
    String key = this.name;
    if (suffix != null)
      key = key + "." + suffix;
    o.setMeta(key,value);
  }

}


