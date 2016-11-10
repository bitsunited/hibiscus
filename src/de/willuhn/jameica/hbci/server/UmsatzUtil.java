/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.hbci.server;

import java.rmi.RemoteException;
import java.util.Date;

import org.apache.commons.lang.StringUtils;

import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.hbci.HBCI;
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.rmi.HBCIDBService;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.util.DateUtil;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;


/**
 * Hilfsklasse zum Verarbeiten von Umsaetzen.
 * 
 */
public class UmsatzUtil
{
  /**
   * Liefert alle Umsaetze in chronologischer Reihenfolge (alte zuerst) sortiert nach Datum, ID.
   * Weitere Filter-Kriterien wie Zeitraum und Konto muessen noch hinzugefuegt werden.
   * Die Funktion sortiert lediglich vereinheitlicht.
   * @return sortierte Liste der Umsaetze.
   * @throws RemoteException
   */
  public static DBIterator getUmsaetze() throws RemoteException
  {
    return getUmsaetze(false);
  }

  /**
   * Liefert alle Umsaetze in umgekehrt chronologischer Reihenfolge (neue zuerst) sortiert nach Datum, ID.
   * Weitere Filter-Kriterien wie Zeitraum und Konto muessen noch hinzugefuegt werden.
   * Die Funktion sortiert lediglich vereinheitlicht.
   * @return sortierte Liste der Umsaetze.
   * @throws RemoteException
   */
  public static DBIterator getUmsaetzeBackwards() throws RemoteException
  {
    return getUmsaetze(true);
  }
  
  /**
   * Liefert alle Umsaetze in ugekehrt chronologischer Reihenfolge (neue zuerst), die den Kriterien entsprechen.
   * @param konto das Konto. Optional.
   * @param kategorie Konto-Kategorie. Optional.
   * @param from das Start-Datum. Optional.
   * @param to das End-Datum. Optional.
   * @param query Suchbegriff. Optional.
   * @return Liste der gefundenen Umsaetze.
   * @throws RemoteException
   */
  public static DBIterator find(Konto konto, String kategorie, Date from, Date to, String query) throws RemoteException
  {
    DBIterator list = getUmsaetzeBackwards();
    
    if (konto != null)
      list.addFilter("konto_id = " + konto.getID());
    else if (StringUtils.trimToNull(kategorie) != null)
      list.addFilter("konto_id in (select id from konto where kategorie = ?)", kategorie);
    
    if (from != null)
      list.addFilter("datum >= ?", new java.sql.Date(DateUtil.startOfDay(from).getTime()));
    if (to != null)
      list.addFilter("datum <= ?", new java.sql.Date(DateUtil.endOfDay(to).getTime()));
    
    if (StringUtils.trimToNull(query) != null)
    {
      String text = "%" + query.toLowerCase() + "%";
      list.addFilter("(LOWER(CONCAT(COALESCE(zweck,''),COALESCE(zweck2,''),COALESCE(zweck3,''))) LIKE ? OR " +
          "LOWER(empfaenger_name) LIKE ? OR " +
          "empfaenger_konto LIKE ? OR " +
          "empfaenger_blz LIKE ? OR " +
          "LOWER(primanota) LIKE ? OR " +
          "LOWER(art) LIKE ? OR " +
          "LOWER(customerref) LIKE ? OR " +
          "LOWER(kommentar) LIKE ?)",
          text,text,text,text,text,text,text,text);
    }
    return list;
  }

  /**
   * Liefert alle Umsaetze in umgekehrt chronologischer Reihenfolge (neue zuerst), in denen
   * der genannte Suchbegriff auftaucht.
   * @param query Suchbegriff.
   * @return Liste der gefundenen Umsaetze.
   * @throws RemoteException
   * @throws ApplicationException wird geworfen, wenn kein Suchbegriff angegeben ist.
   */
  public static DBIterator find(String query) throws RemoteException, ApplicationException
  {
    if (query == null || query.length() == 0)
    {
      I18N i18n = Application.getPluginLoader().getPlugin(HBCI.class).getResources().getI18N();
      throw new ApplicationException(i18n.tr("Bitte geben Sie einen Suchbegriff an"));
    }
    
    return find(null,null,null,null,query);
  }

  /**
   * Liefert alle Umsaetze, jedoch mit vereinheitlichter Vorsortierung.
   * @param backwards chronologisch (alte zuerst) = true.
   * umgekehrt chronologisch (neue zuerst) = false.
   * @return sortierte Liste der Umsaetze.
   * @throws RemoteException
   */
  private static DBIterator getUmsaetze(boolean backwards) throws RemoteException
  {
    String s = backwards ? "DESC" : "ASC";
    HBCIDBService service = (HBCIDBService) Settings.getDBService();
    DBIterator list = service.createList(Umsatz.class);
    list.setOrder("ORDER BY " + service.getSQLTimestamp("datum") + " " + s + ", id " + s);
    return list;
  }
}
