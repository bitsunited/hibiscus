/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/
package de.willuhn.jameica.hbci.server;

import java.rmi.RemoteException;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.kapott.hbci.GV_Result.GVRDauerList;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Saldo;
import org.kapott.hbci.structures.Value;
import org.kapott.hbci.swift.DTAUS;

import de.jost_net.OBanToo.SEPA.IBAN;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.hbci.HBCIProperties;
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.rmi.Address;
import de.willuhn.jameica.hbci.rmi.HibiscusAddress;
import de.willuhn.jameica.hbci.rmi.KontoType;
import de.willuhn.jameica.hbci.rmi.SammelLastschrift;
import de.willuhn.jameica.hbci.rmi.SammelTransfer;
import de.willuhn.jameica.hbci.rmi.SammelTransferBuchung;
import de.willuhn.jameica.hbci.rmi.SammelUeberweisung;
import de.willuhn.jameica.hbci.rmi.SepaDauerauftrag;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.server.VerwendungszweckUtil.Tag;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;

/**
 * Hilfeklasse, welche Objekte aus HBCI4Java in unsere Datenstrukturen konvertiert
 * und umgekehrt.
 */
public class Converter
{

  /**
   * Konvertiert einen einzelnen Umsatz von HBCI4Java nach Hibiscus.
   * Wichtig: Das zugeordnete Konto wird nicht gefuellt. Es ist daher Sache 
   * des Aufrufers, noch die Funktion <code>umsatz.setKonto(Konto)</code> aufzurufen,
   * damit das Objekt in der Datenbank gespeichert werden kann.
   * @param u der zu convertierende Umsatz.
   * @return das neu erzeugte Umsatz-Objekt.
   * @throws RemoteException
   */
  public static Umsatz HBCIUmsatz2HibiscusUmsatz(GVRKUms.UmsLine u) throws RemoteException
  {
    Umsatz umsatz = (Umsatz) Settings.getDBService().createObject(Umsatz.class,null);

    umsatz.setArt(clean(u.text));
    umsatz.setCustomerRef(clean(u.customerref));
    umsatz.setPrimanota(clean(u.primanota));

    double kurs = 1.95583;

    //BUGZILLA 67 http://www.willuhn.de/bugzilla/show_bug.cgi?id=67
    Saldo s = u.saldo;
    if (s != null)
    {
      Value v = s.value;
      if (v != null)
      {
        // BUGZILLA 318
        double saldo = v.getDoubleValue();
        String curr  = v.getCurr();
        if (curr != null && "DEM".equals(curr))
          saldo /= kurs;
        umsatz.setSaldo(saldo);
      }
    }

    Value v = u.value;
    double betrag = v.getDoubleValue();
    String curr = v.getCurr();

    // BUGZILLA 318
    if (curr != null && "DEM".equals(curr))
      betrag /= kurs;

    umsatz.setBetrag(betrag);
    umsatz.setDatum(u.bdate);
    umsatz.setValuta(u.valuta);

    // Wir uebernehmen den GV-Code nur, wenn was sinnvolles drin steht.
    // "999" steht hierbei fuer unstrukturiert aka unbekannt.
    // 
    if (u.gvcode != null && !u.gvcode.equals("999") && u.gvcode.length() <= HBCIProperties.HBCI_GVCODE_MAXLENGTH)
      umsatz.setGvCode(u.gvcode);

    if (u.addkey != null && u.addkey.length() > 0 && u.addkey.length() <= HBCIProperties.HBCI_ADDKEY_MAXLENGTH)
      umsatz.setAddKey(u.addkey);

    ////////////////////////////////////////////////////////////////////////////
    // Verwendungszweck

    // BUGZILLA 146
    // Aus einer Mail von Stefan Palme
    //    Es geht noch besser. Wenn in "umsline.gvcode" nicht der Wert "999"
    //    drinsteht, sind die Variablen "text", "primanota", "usage", "other"
    //    und "addkey" irgendwie sinnvoll gef�llt.  Steht in "gvcode" der Wert
    //    "999" drin, dann sind diese Variablen alle null, und der ungeparste 
    //    Inhalt des Feldes :86: steht komplett in "additional".

    String[] lines = (String[]) u.usage.toArray(new String[u.usage.size()]);

    // die Bank liefert keine strukturierten Verwendungszwecke (gvcode=999).
    // Daher verwenden wir den gesamten "additional"-Block und zerlegen ihn
    // in 27-Zeichen lange Haeppchen
    if (lines.length == 0)
      lines = VerwendungszweckUtil.parse(u.additional);

    // Es gibt eine erste Bank, die 40 Zeichen lange Verwendungszwecke lieferte.
    // Siehe Mail von Frank vom 06.02.2014
    lines = VerwendungszweckUtil.rewrap(35,lines);
    VerwendungszweckUtil.apply(umsatz,lines);
    //
    ////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////
    // Gegenkonto
    // und jetzt noch der Empfaenger (wenn er existiert)
    if (u.other != null) 
    {
      HibiscusAddress a = HBCIKonto2Address(u.other);
      // Wenn keine Kontonummer/BLZ angegeben ist, versuchen wir es mit BIC/IBAN
      if (a.getKontonummer() == null || a.getKontonummer().length() == 0)
        a.setKontonummer(a.getIban());
      if (a.getBlz() == null || a.getBlz().length() == 0)
        a.setBlz(a.getBic());
      umsatz.setGegenkonto(a);
    }

    if (!HBCIProperties.HBCI_SEPA_PARSE_TAGS)
      return umsatz;

    // Wenn wir noch keine Gegenkonto-Infos haben, versuchen wir mal, sie aus
    // dem Verwendungszweck zu extrahieren
    boolean haveIban = StringUtils.trimToNull(umsatz.getGegenkontoNummer()) != null;
    boolean haveBic  = StringUtils.trimToNull(umsatz.getGegenkontoBLZ()) != null;
    boolean haveName = StringUtils.trimToNull(umsatz.getGegenkontoName()) != null;

    if (!haveIban || !haveBic || !haveName)
    {
      Map<Tag,String> tags = VerwendungszweckUtil.parse(umsatz);

      if (!haveName)
        umsatz.setGegenkontoName(tags.get(Tag.ABWA));

      String iban = tags.get(Tag.IBAN);
      String bic  = tags.get(Tag.BIC);

      IBAN i = null;

      if (!haveIban && StringUtils.trimToNull(iban) != null)
      {
        // Nur uebernehmen, wenn es eine gueltige IBAN ist
        try
        {
          i = HBCIProperties.getIBAN(iban);
          if (i != null)
            umsatz.setGegenkontoNummer(i.getIBAN());
        }
        catch (Exception e)
        {
          Logger.error("invalid IBAN - ignoring: " + iban,e);
        }
      }

      if (!haveBic)
      {
        bic = StringUtils.trimToNull(bic);
        if (bic != null)
        {
          try
          {
            bic = HBCIProperties.checkBIC(bic);
            if (bic != null)
              umsatz.setGegenkontoBLZ(bic);
          }
          catch (Exception e)
          {
            Logger.error("invalid BIC - ignoring: " + bic,e);
          }
        }
        else if (i != null)
        {
          umsatz.setGegenkontoBLZ(i.getBIC());
        }
      }
    }

    //
    ////////////////////////////////////////////////////////////////////////////
    return umsatz;
  }

  /**
   * Entfernt Zeichen, die in den Strings nicht enthalten sein sollten.
   * Typischerweise Zeilenumbrueche.
   * @param s der String.
   * @return der bereinigte String.
   * BUGZILLA 1611
   */
  private static String clean(String s)
  {
    return HBCIProperties.replace(s,HBCIProperties.TEXT_REPLACEMENTS_UMSATZ);
  }

  /**
   * Konvertiert eine Zeile aus der Liste der abgerufenen SEPA-Dauerauftraege.
   * @param d der SEPA-Dauerauftrag aus HBCI4Java.
   * @return Unser Dauerauftrag.
   * @throws RemoteException
   * @throws ApplicationException
   */
  public static SepaDauerauftrag HBCIDauer2HibiscusSepaDauerauftrag(GVRDauerList.Dauer d)
      throws RemoteException, ApplicationException
  {
    SepaDauerauftragImpl auftrag = (SepaDauerauftragImpl) Settings.getDBService().createObject(SepaDauerauftrag.class,null);
    auftrag.setErsteZahlung(d.firstdate);
    auftrag.setLetzteZahlung(d.lastdate);

    // Das ist nicht eindeutig. Da der Converter schaut, ob er ein solches
    // Konto schon hat und bei Bedarf das existierende verwendet. Es kann aber
    // sein, dass ein User ein und das selbe Konto mit verschiedenen Sicherheitsmedien
    // bedient. In diesem Fall wird der Dauerauftrag evtl. beim falschen Konto
    // einsortiert. Ist aber kein Problem, weil der HBCIDauerauftragListJob
    // das Konto eh nochmal gegen seines (und er kennt das richtige) ueberschreibt.
    auftrag.setKonto(HBCIKonto2HibiscusKonto(d.my));

    auftrag.setBetrag(d.value.getDoubleValue());
    auftrag.setOrderID(d.orderid);

    // Jetzt noch der Empfaenger
    auftrag.setGegenkonto(HBCIKonto2Address(d.other));

    auftrag.setChangable(d.can_change);
    auftrag.setDeletable(d.can_delete);

    auftrag.setPmtInfId(d.pmtinfid);
    auftrag.setPurposeCode(d.purposecode);

    // Verwendungszweck
    VerwendungszweckUtil.apply(auftrag,d.usage);

    // Es kann wohl Faelle geben, wo der Auftrag keinen Verwendungszweck hat.
    // In dem Fall tragen wir ein "-" ein.
    if (auftrag.getZweck() == null)
      auftrag.setZweck("-");

    auftrag.setTurnus(TurnusHelper.createByDauerAuftrag(d));
    return auftrag;
  }

  /**
   * Konvertiert ein Hibiscus-Konto in ein HBCI4Java Konto.
   * @param konto unser Konto.
   * @return das HBCI4Java Konto.
   * @throws RemoteException
   */
  public static Konto HibiscusKonto2HBCIKonto(de.willuhn.jameica.hbci.rmi.Konto konto) throws RemoteException
  {
    org.kapott.hbci.structures.Konto k = new org.kapott.hbci.structures.Konto(konto.getBLZ(),konto.getKontonummer());
    k.country    = "DE";
    k.curr       = konto.getWaehrung();
    k.customerid = konto.getKundennummer();
    k.type       = konto.getBezeichnung(); // BUGZILLA 338
    k.name       = konto.getName();
    k.subnumber  = konto.getUnterkonto(); // BUGZILLA 355
    k.iban       = konto.getIban();
    k.bic        = konto.getBic();

    Integer accType = konto.getAccountType();
    k.acctype    = accType != null ? accType.toString() : null;
    return k;  	
  }

  /**
   * Konvertiert ein HBCI4Java-Konto in ein Hibiscus Konto.
   * Existiert ein Konto mit dieser Kontonummer und BLZ bereits in Hibiscus,
   * wird jenes stattdessen zurueckgeliefert.
   * @param konto das HBCI4Java Konto.
   * @param passportClass optionale Angabe einer Passport-Klasse. Ist er angegeben wird, nur dann ein existierendes Konto
   * verwendet, wenn neben Kontonummer und BLZ auch die Klasse des Passport uebereinstimmt.
   * @return unser Konto.
   * @throws RemoteException
   */
  public static de.willuhn.jameica.hbci.rmi.Konto HBCIKonto2HibiscusKonto(Konto konto, Class passportClass) throws RemoteException
  {
    DBIterator list = Settings.getDBService().createList(de.willuhn.jameica.hbci.rmi.Konto.class);
    list.addFilter("kontonummer = ?", new Object[]{konto.number});
    list.addFilter("blz = ?",         new Object[]{konto.blz});
    if (passportClass != null)
      list.addFilter("passport_class = ?", new Object[]{passportClass.getName()});

    // BUGZILLA 355
    if (konto.subnumber != null && konto.subnumber.length() > 0)
      list.addFilter("unterkonto = ?",new Object[]{konto.subnumber});

    // BUGZILLA 338: Wenn das Konto eine Bezeichnung hat, muss sie uebereinstimmen
    if (konto.type != null && konto.type.length() > 0)
      list.addFilter("bezeichnung = ?", new Object[]{konto.type});

    // Wir vervollstaendigen gleich noch die Kontoart, wenn wir eine haben und im Konto noch
    // keine hinterlegt ist.
    String type = StringUtils.trimToNull(konto.acctype);
    Integer accType = null;
    if (type != null)
    {
      try
      {
        accType = Integer.parseInt(type);
      }
      catch (Exception e)
      {
        Logger.error("unknown account type: " + type,e);
      }
    }

    if (list.hasNext())
    {
      // Konto gibts schon
      de.willuhn.jameica.hbci.rmi.Konto result = (de.willuhn.jameica.hbci.rmi.Konto) list.next();
      Integer current = result.getAccountType();
      if ((current == null && accType != null) || (current != null && accType != null && !current.equals(accType))) // Neu oder hat sich geaendert
      {
        try
        {
          KontoType kt = KontoType.find(accType);
          Logger.info("auto-completing account type (value: " + accType + " - " + kt + ", old value: " + current + ") for account ID: " + result.getID());
          result.setAccountType(accType);
          result.store();
        }
        catch (Exception e)
        {
          Logger.error("unable to auto-complete account type (value: " + accType + ") for account ID: " + result.getID(),e);
        }
      }

      return result;
    }

    // Ne, wir erstellen ein neues
    de.willuhn.jameica.hbci.rmi.Konto k = (de.willuhn.jameica.hbci.rmi.Konto) Settings.getDBService().createObject(de.willuhn.jameica.hbci.rmi.Konto.class,null);
    k.setBLZ(konto.blz);
    k.setKontonummer(konto.number);
    k.setUnterkonto(konto.subnumber); // BUGZILLA 355
    k.setKundennummer(konto.customerid);
    k.setName(konto.name);
    k.setBezeichnung(konto.type);
    k.setWaehrung(konto.curr);
    k.setIban(konto.iban);
    k.setAccountType(accType);
    k.setBic(konto.bic);
    if (passportClass != null)
      k.setPassportClass(passportClass.getName());
    return k;  	
  }

  /**
   * Konvertiert ein HBCI4Java-Konto in ein Hibiscus Konto.
   * Existiert ein Konto mit dieser Kontonummer und BLZ bereits in Hibiscus,
   * wird jenes stattdessen zurueckgeliefert.
   * @param konto das HBCI4Java Konto.
   * @return unser Konto.
   * @throws RemoteException
   */
  public static de.willuhn.jameica.hbci.rmi.Konto HBCIKonto2HibiscusKonto(Konto konto) throws RemoteException
  {
    return HBCIKonto2HibiscusKonto(konto,null);
  }

  /**
   * Konvertiert einen Hibiscus-Adresse in ein HBCI4Java Konto.
   * @param adresse unsere Adresse.
   * @return das HBCI4Java Konto.
   * @throws RemoteException
   */
  public static Konto Address2HBCIKonto(Address adresse) throws RemoteException
  {
    Konto k = new Konto("DE",adresse.getBlz(),adresse.getKontonummer());
    k.name = adresse.getName();
    k.iban = adresse.getIban();
    k.bic  = adresse.getBic();
    return k;
  }

  /**
   * Konvertiert ein HBCI4Java Konto in eine Hibiscus-Adresse.
   * @param konto das HBCI-Konto.
   * @return unsere Adresse.
   * @throws RemoteException
   */
  public static HibiscusAddress HBCIKonto2Address(Konto konto) throws RemoteException
  {
    HibiscusAddress e = (HibiscusAddress) Settings.getDBService().createObject(HibiscusAddress.class,null);
    e.setBlz(konto.blz);
    e.setKontonummer(konto.number);
    e.setBic(konto.bic);
    e.setIban(konto.iban);

    String name  = StringUtils.trimToEmpty(konto.name);
    String name2 = StringUtils.trimToEmpty(konto.name2);

    if (name2 != null && name2.length() > 0)
      name += (" " + name2);

    if (name != null && name.length() > HBCIProperties.HBCI_TRANSFER_NAME_MAXLENGTH)
      name = name.substring(0,HBCIProperties.HBCI_TRANSFER_NAME_MAXLENGTH);
    e.setName(name);
    return e;  	
  }

  /**
   * Konvertiert eine Sammel-Ueberweisung in DTAUS-Format.
   * @param su Sammel-Ueberweisung.
   * @return DTAUS-Repraesentation.
   * @throws RemoteException
   */
  public static DTAUS HibiscusSammelUeberweisung2DTAUS(SammelUeberweisung su) throws RemoteException
  {
    // TYPE_CREDIT = Sammel�berweisung
    // TYPE_DEBIT = Sammellastschrift
    return HibiscusSammelTransfer2DTAUS(su, DTAUS.TYPE_CREDIT);
  }

  /**
   * Konvertiert eine Sammel-Lastschrift in DTAUS-Format.
   * @param sl Sammel-Lastschrift.
   * @return DTAUS-Repraesentation.
   * @throws RemoteException
   */
  public static DTAUS HibiscusSammelLastschrift2DTAUS(SammelLastschrift sl) throws RemoteException
  {
    // TYPE_CREDIT = Sammel�berweisung
    // TYPE_DEBIT = Sammellastschrift
    return HibiscusSammelTransfer2DTAUS(sl, DTAUS.TYPE_DEBIT);
  }

  /**
   * Hilfsfunktion. Ist private, damit niemand aus Versehen den falschen Type angibt.
   * @param s Sammel-Transfer.
   * @param type Art des Transfers.
   * @see DTAUS#TYPE_CREDIT
   * @see DTAUS#TYPE_DEBIT
   * @return DTAUS-Repraesentation.
   * @throws RemoteException
   */
  private static DTAUS HibiscusSammelTransfer2DTAUS(SammelTransfer s, int type) throws RemoteException
  {

    DTAUS dtaus = new DTAUS(HibiscusKonto2HBCIKonto(s.getKonto()),type);
    DBIterator buchungen = s.getBuchungen();
    SammelTransferBuchung b = null;
    while (buchungen.hasNext())
    {
      b = (SammelTransferBuchung) buchungen.next();
      final DTAUS.Transaction tr = dtaus.new Transaction();

      Konto other = new Konto("DE",b.getGegenkontoBLZ(),b.getGegenkontoNummer());
      other.name = b.getGegenkontoName();

      tr.otherAccount = other;
      tr.value = new Value(String.valueOf(b.getBetrag()));

      String key = b.getTextSchluessel();
      if (key != null && key.length() > 0)
        tr.key = key; // Nur setzen, wenn in der Buchung definiert. Gibt sonst in DTAUS#toString eine NPE

      String[] lines = VerwendungszweckUtil.toArray(b);
      for (String line:lines)
      {
        tr.addUsage(line);
      }

      dtaus.addEntry(tr);
    }
    return dtaus;
  }

}
