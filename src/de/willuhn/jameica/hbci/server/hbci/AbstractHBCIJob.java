/**********************************************************************
 * $Source: /cvsroot/hibiscus/hibiscus/src/de/willuhn/jameica/hbci/server/hbci/AbstractHBCIJob.java,v $
 * $Revision: 1.41 $
 * $Date: 2012/03/01 22:19:15 $
 * $Author: willuhn $
 * $Locker:  $
 * $State: Exp $
 *
 * Copyright (c) by willuhn.webdesign
 * All rights reserved
 *
 **********************************************************************/
package de.willuhn.jameica.hbci.server.hbci;

import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;

import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.manager.HBCIUtilsInternal;
import org.kapott.hbci.status.HBCIRetVal;
import org.kapott.hbci.status.HBCIStatus;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;

import de.willuhn.jameica.hbci.HBCI;
import de.willuhn.jameica.hbci.rmi.HibiscusDBObject;
import de.willuhn.jameica.hbci.rmi.Transfer;
import de.willuhn.jameica.hbci.server.VerwendungszweckUtil;
import de.willuhn.jameica.hbci.synchronize.SynchronizeSession;
import de.willuhn.jameica.hbci.synchronize.hbci.HBCISynchronizeBackend;
import de.willuhn.jameica.services.BeanService;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;
import de.willuhn.util.ProgressMonitor;

/**
 * Basis-Klasse fuer die HBCI-Jobs.
 * Ein HBCI-Job muss quasi atomar sein. Das heisst, in dessen <code>handleResult</code>
 * nimmt er auch gleich ggf. notwendige Aenderungen <b>und</b> Speicherungen
 * an den betroffenen Fachobjekten vor. Grund: Es darf nicht sein, dass zB.
 * eine Ueberweisung ausgefuehrt wird, ihr Status jedoch in der DB nicht auf
 * "ausgefuehrt" gesetzt wird.
 */
public abstract class AbstractHBCIJob
{
  protected final static I18N i18n = Application.getPluginLoader().getPlugin(HBCI.class).getResources().getI18N();

	private org.kapott.hbci.GV.HBCIJob job = null;
  private boolean exclusive              = false;
	private Hashtable params 			         = new Hashtable(); 

	/**
	 * HBCI4Java verwendet intern eindeutige Job-Namen.
	 * Diese Funktion liefert genau den Namen fuer genau den
	 * gewuenschten Job.
	 * @return Job-Identifier.
	 */
  public abstract String getIdentifier();
  
  /**
   * Liefert einen sprechenden Namen fuer diesen Job.
   * @return sprechender Name.
   * @throws RemoteException
   */
  public abstract String getName() throws RemoteException;

  /**
   * Markiert den Auftrag als erledigt.
   * @throws RemoteException
   * @throws ApplicationException
   */
  protected abstract void markExecuted() throws RemoteException, ApplicationException;
  
  /**
   * Markiert den Auftrag als fehlerhaft.
   * @param error der Fehlertext aus der HBCI-Nachricht.
   * @return der Fehlertext, wie er weitergeworfen werden soll.
   * Hier kann der Implementierer noch weitere Informationen zum Job hinzufuegen.
   * @throws RemoteException
   * @throws ApplicationException
   */
  protected abstract String markFailed(String error) throws RemoteException, ApplicationException;
  
  /**
   * Liefert den zugehoerigen Auftrag von Hibiscus - insofern verfuegbar.
   * @return der zugehoerige Auftrag von Hibiscus - insofern verfuegbar.
   */
  protected abstract HibiscusDBObject getContext();

  /**
   * Wird aufgerufen, wenn der User den Vorgang abgebrochen hat.
   * Kann von den Jobs implementiert werden, muss aber nicht.
   * Die Funktion wird nur genau dann aufgerufen, wenn der Job noch abbrechbar war - sprich,
   * wenn er noch nicht an die Bank uebertragen wurde. Wurde er jedoch bereits an die Bank
   * gesendet, dann wird entweder markFailed() oder markExecuted() aufgerufen.
   * @throws RemoteException
   * @throws ApplicationException
   * BUGZILLA 690
   */
  protected void markCancelled() throws RemoteException, ApplicationException
  {
  }
  
	/**
	 * Diese Funktion wird vom HBCISynchronizeBackend intern aufgerufen.
	 * Sie uebergibt hier den erzeugten HBCI-Job der Abfrage.
	 * @param job der erzeugte Job.
   * @throws RemoteException
   * @throws ApplicationException
	 */
  public void setJob(org.kapott.hbci.GV.HBCIJob job) throws RemoteException, ApplicationException
  {
  	this.job = job;
  	
  	HibiscusDBObject t = this.getContext();
  	this.job.setExternalId(HBCIContext.serialize(t));
  	
  	if (t != null)
  	{
  	  
  	}
  	
  	Iterator i = params.keySet().iterator();
  	while (i.hasNext())
  	{
  		Object key = i.next();
  		Object value = params.get(key);
  		
  		String name = null;
  		Integer idx = null;
  		if (key instanceof SimpleEntry)
  		{
  		  name = (String) ((SimpleEntry) key).getKey();
  		  idx  = (Integer) ((SimpleEntry) key).getValue();
  		}
  		else
  		{
  		  name = (String) key;
  		}
  		
  		if (idx != null)
  		{
        if (value instanceof Konto)
          job.setParam(name,idx,(Konto)value);

        else if (value instanceof Date)
          job.setParam(name,idx,(Date)value);

        else if (value instanceof Value)
          job.setParam(name,idx,(Value)value);

        else
          job.setParam(name,idx,value.toString());
  		}
  		else
  		{
  		  // Wenn idx null ist, muessen die alten Funktionen
  		  // ausgefuehrt werden, weil die in einigen GV-Klassen ueberschrieben wurden.
  		  // Z.Bsp. um bei DEs des Typ "bin" ein "B" davor zu schreiben. Z.Bsp. bei
  		  // den IZV-Sammelauftraegen. Die werden sonst von SyntaxBin.expand nicht mehr
  		  // als Binaer-Daten erkannt.
        if (value instanceof Konto)
          job.setParam(name,(Konto)value);

        else if (value instanceof Date)
          job.setParam(name,(Date)value);

        else if (value instanceof Value)
          job.setParam(name,(Value)value);

        else
          job.setParam(name,value.toString());
  		}
  	}
  }

  /**
   * Liefert das Job-Resultat.
   * @return Job-Resultat.
   */
  protected final HBCIJobResult getJobResult()
	{
		return job.getJobResult();
	}
  
  /**
   * Diese Funktion wird von der HBCIFactory nach Beendigung der Kommunikation mit der Bank ausgefuehrt.
   * Sie prueft globalen Status und Job-Status und ruft entsprechend markExecuted() oder markFailed(String) auf. 
   * @throws RemoteException
   * @throws ApplicationException
   */
  public final void handleResult() throws ApplicationException, RemoteException
  {
    HBCIJobResult result    = getJobResult();
    HBCIStatus status       = result.getJobStatus();

    // BUGZILLA 964 - nur dann als abgebrochen markieren, wenn wir fuer den Job noch keinen richtigen
    // Status haben. Denn wenn der vorliegt, ist es fuer den Abbruch - zumindest fuer diesen Auftrag - zu spaet.
    // BUGZILLA 1109 - Wenn im ChipTAN-Dialog abgebrochen wird, haben wir hier ein HBCIStatus.STATUS_OK. Das
    // ist ziemlich daemlich. Wegen 964 koennen wir aber nicht pauschal abbrechen, weil wir sonst Jobs
    // als abgebrochen markieren, die schon ausgefuehrt wurden. In dem Status steht OK, drin, weil die Bank da
    // mit SUCCESS-Statuscode sowas hier geschickt hat: "0030 Auftrag entgegengenommen. Bitte TAN eingeben"
    // Rein via Status-Codes sieht alles OK aus. Gemaess "FinTS_3.0_Rueckmeldungscodes_2010-10-27_final_version.pdf"
    // steht "0030" fuer "Auftrag empfangen - Sicherheitsfreigabe erforderlich". Wir machen hier also einen
    // Sonderfall fuer diesen einen Code.
    
    // Das koennte man vermutlich auch direkt in HBCI4Java implementieren
    boolean tanNeeded = false;
    boolean executed  = false;
    HBCIRetVal[] values = status.getSuccess();
    if (values != null && values.length > 0)
    {
      for (HBCIRetVal val:values)
      {
        if (val.code != null)
        {
          tanNeeded |= val.code.equals("0030");
          executed  |= (val.code.equals("0010") || val.code.equals("0020"));
        }
      }
    }

    BeanService service = Application.getBootLoader().getBootable(BeanService.class);
    SynchronizeSession session = service.get(HBCISynchronizeBackend.class).getCurrentSession();
    
    ////////////////////////////////////////////////////////////////////////////
    // Warnungen ausgeben, falls vorhanden - BUGZILLA 899
    HBCIRetVal[] warnings = status.getWarnings();
    if (warnings != null && warnings.length > 0)
    {
      // Loggen
      ProgressMonitor monitor = session.getProgressMonitor();
      monitor.log(" ");
      for (HBCIRetVal val:warnings)
        monitor.log("  " + val.code + ": " + val.text);
      monitor.log(" ");
    }
    ////////////////////////////////////////////////////////////////////////////

    final String errorText = this.getErrorText();

    // BUGZILLA 1283 - Job wurde zweifelsfrei ausgefuehrt
    // Bei meinem Test war es so, dass beim Abbruch bei der zweiten TAN-Eingabe auch bei dem ersten
    // Auftrag der "0030" enthalten war. Sah dann so aus:
    
    // HBCISynchronizeBackend$HBCIJobGroup.sync] executing check for job UebSEPA
    // ..
    // AbstractHBCIJob.getStatusText] retval[ 0]: Auftrag empfangen - Bitte die empfangene Tan eingeben.
    // AbstractHBCIJob.getStatusText] retval[ 1]: Der Auftrag wurde entgegengenommen.
    // AbstractHBCIJob.handleResult] hbci session cancelled by user, mark job as cancelled
    // HBCISynchronizeBackend$HBCIJobGroup.sync] executing check for job UebSEPA
    // ..
    // AbstractHBCIJob.getStatusText] retval[ 0]: Auftrag empfangen - Bitte die empfangene Tan eingeben.
    // AbstractHBCIJob.handleResult] hbci session cancelled by user, mark job as cancelled
    
    // Bei dem abgebrochenen fehlte das "Der Auftrag wurde entgegengenommen.". Bei beiden war aber der "0030"
    // enthalten. Daher pruefen wir hier nach Vorhandensein von 0010/0020
    if (executed && status.isOK())
    {
      markExecutedInternal(errorText);
      return;
    }
    
    if ((tanNeeded || status.getStatusCode() == HBCIStatus.STATUS_UNKNOWN) && session.getStatus() == ProgressMonitor.STATUS_CANCEL) // BUGZILLA 690
    {
      Logger.warn("hbci session cancelled by user, mark job as cancelled");
      markCancelled();
      return;
    }

    // Globaler Status ist OK - Job wurde zweifelsfrei erfolgreich ausgefuehrt
    // Wir markieren die Ueberweisung als "ausgefuehrt"
    if (result.isOK())
    {
      markExecutedInternal(errorText);
      return;
    }

    // Globaler Status ist nicht OK. Mal schauen, was der Job-Status sagt
    if (status.isOK())
    {
      // Wir haben zwar global einen Fehler. Aber zumindest der Auftrag
      // scheint in Ordnung zu sein. Wir markieren ihn sicherheitshalber
      // als ausgefuehrt (damit er nicht mehrfach ausgefuhert wird), melden
      // den globalen Fehler aber trotzdem weiter
      markExecutedInternal(errorText);
      return;
    }

    // Nichts hat geklappt. Weder der globale Status ist in Ordnung
    // noch der Job-Status. Wir geben dem Job die Moeglichkeit, ihn
    // als fehlerhaft zu markieren.
    String error = null;
    try
    {
      error = markFailed(errorText);
    }
    catch (Exception e)
    {
      // Folge-Fehler. Loggen. Aber originale Meldung weiterwerfen
      Logger.error("unable to mark job as failed",e);
    }
    throw new ApplicationException(error != null && error.length() > 0 ? error : errorText);
  }
  
  /**
   * Markiert den Auftrag als ausgefuehrt und uebernimmt das Fehlerhandling.
   * @param errorText der anzuzeigende Fehlertext.
   * @throws ApplicationException
   */
  private void markExecutedInternal(final String errorText) throws ApplicationException
  {
    // Wir haben zwar global einen Fehler. Aber zumindest der Auftrag
    // scheint in Ordnung zu sein. Wir markieren ihn sicherheitshalber
    // als ausgefuehrt (damit er nicht mehrfach ausgefuhert wird), melden
    // den globalen Fehler aber trotzdem weiter
    try
    {
      markExecuted();
    }
    catch (ApplicationException ae)
    {
      throw ae;
    }
    catch (Exception e)
    {
      // Das ist ein Folge-Fehler. Den loggen wir. Wir werfen aber die originale
      // Fehlermeldung weiter
      Logger.error("unable to mark job as executed",e);
      throw new ApplicationException(errorText);
    }
  }
  
	/**
	 * Liefert den Fehler-Text, der die Rueckemldungen der Bank enthaelt.
   */
  private final String getErrorText()
	{
    String sr = "";
		try
		{
      String sGlob = getJobResult().getGlobStatus().getErrorString();
      Logger.info("global status: " + sGlob);

      String sJob = getJobResult().getJobStatus().getErrorString();
      Logger.info("job status: " + sJob);
      
      HBCIRetVal[] retValues = getJobResult().getJobStatus().getRetVals();
      StringBuffer sb = new StringBuffer();
      for (int i=0;i<retValues.length;++i)
      {
        Logger.info("retval[" + i + "]: " + retValues[i].code + " - " + retValues[i].text);
        sb.append(retValues[i].code + " - " + retValues[i].text);
        if (i < (retValues.length - 1))
          sb.append(", ");
      }
      String sDetail = sb.toString();
      if (sDetail != null && sDetail.length() > 0)
        sr += System.getProperty("line.separator","\n") + sDetail;
      if (sJob != null && sJob.length() > 0)
        sr += System.getProperty("line.separator","\n") + sJob;
      if (sGlob != null && sGlob.length() > 0)
        sr += System.getProperty("line.separator","\n") + sGlob;
		}
		catch (ArrayIndexOutOfBoundsException aio)
		{
			// skip
		}
		catch (Exception e2)
		{
			Logger.error("error while reading status text",e2);
		}
    
    I18N i18n = Application.getPluginLoader().getPlugin(HBCI.class).getResources().getI18N();
    if (sr != null && sr.length() > 0)
      return i18n.tr("Fehlermeldung der Bank: {0}",sr);
    return i18n.tr("Unbekannter Fehler");
	}

	/**
	 * Ueber diese Funktion koennen die konkreten Implementierungen
	 * ihre zusaetzlichen Job-Parameter setzen.
   * @param name Name des Parameters.
   * @param value Wert des Parameters.
   */
  protected final void setJobParam(String name, String value)
	{
    this.setJobParam(name,null,value);
	}
  
  /**
   * Ueber diese Funktion koennen die konkreten Implementierungen
   * ihre zusaetzlichen Job-Parameter setzen.
   * @param name Name des Parameters.
   * @param index optionaler Index des Parameters.
   * @param value Wert des Parameters.
   */
  protected final void setJobParam(String name, Integer index, String value)
  {
    if (name == null || value == null)
    {
      Logger.warn("[job parameter] no name or value given");
      return;
    }
    params.put(new AbstractMap.SimpleEntry(name,index),value);
  }

	/**
	 * Speichern eines komplexes Objektes 
	 * @param name Name des Parameters.
	 * @param konto das Konto.
	 */
	protected final void setJobParam(String name, org.kapott.hbci.structures.Konto konto)
	{
	  this.setJobParam(name,null,konto);
	}

  /**
   * Speichern eines komplexes Objektes 
   * @param name Name des Parameters.
   * @param index optionaler Index des Parameters.
   * @param konto das Konto.
   */
  protected final void setJobParam(String name, Integer index, org.kapott.hbci.structures.Konto konto)
  {
    if (name == null || konto == null)
    {
      Logger.warn("[job parameter] no name or value given");
      return;
    }
    params.put(new AbstractMap.SimpleEntry(name,index),konto);
  }

	/**
	 * Speichern eines Int-Wertes.
	 * Bitte diese Funktion verwenden, damit sichergestellt ist, dass
	 * der Kernel die Werte typsicher erhaelt und Formatierungsfehler
	 * aufgrund verschiedener Locales fehlschlagen.
   * @param name Name des Parameters.
   * @param i Wert.
   */
  protected final void setJobParam(String name, int i)
	{
		if (name == null)
		{
			Logger.warn("[job parameter] no name given");
			return;
		}
		params.put(name,new Integer(i));
	}

  /**
	 * Speichern eines Geld-Betrages
	 * Bitte diese Funktion fuer Betraege verwenden, damit sichergestellt ist,
	 * dass der Kernel die Werte typsicher erhaelt und Formatierungsfehler
	 * aufgrund verschiedener Locales fehlschlagen.
	 * @param name Name des Parameters.
   * @param value Geldbetrag.
   * @param currency Waehrung.
	 */
	protected final void setJobParam(String name, double value, String currency)
	{
	  this.setJobParam(name,null,value,currency);
	}
	
  /**
   * Speichern eines Geld-Betrages
   * Bitte diese Funktion fuer Betraege verwenden, damit sichergestellt ist,
   * dass der Kernel die Werte typsicher erhaelt und Formatierungsfehler
   * aufgrund verschiedener Locales fehlschlagen.
   * @param name Name des Parameters.
   * @param index optionaler Index des Parameters.
   * @param value Geldbetrag.
   * @param currency Waehrung.
   */
  protected final void setJobParam(String name, Integer index, double value, String currency)
  {
    if (name == null)
    {
      Logger.warn("[job parameter] no name given");
      return;
    }
    
    BigDecimal bd = new BigDecimal(value).setScale(2,BigDecimal.ROUND_HALF_EVEN);
    params.put(new AbstractMap.SimpleEntry(name,index),new Value(bd,currency));
  }

	/**
	 * Speichern eines Datums.
	 * Bitte diese Funktion verwenden, damit sichergestellt ist, dass
	 * der Kernel die Werte typsicher erhaelt und Formatierungsfehler
	 * aufgrund verschiedener Locales fehlschlagen.
	 * @param name Name des Parameters.
	 * @param date Datum.
	 */
	protected final void setJobParam(String name, Date date)
	{
	  this.setJobParam(name,null,date);
	}
	
  /**
   * Speichern eines Datums.
   * Bitte diese Funktion verwenden, damit sichergestellt ist, dass
   * der Kernel die Werte typsicher erhaelt und Formatierungsfehler
   * aufgrund verschiedener Locales fehlschlagen.
   * @param name Name des Parameters.
   * @param index optionaler Index des Parameters.
   * @param date Datum.
   */
  protected final void setJobParam(String name, Integer index, Date date)
  {
    if (name == null || date == null)
    {
      Logger.warn("[job parameter] no name given or value given");
      return;
    }
    params.put(new AbstractMap.SimpleEntry(name,index),date);
  }

	/**
	 * Setzt die Job-Parameter fuer die Verwendungszweck-Zeilen.
	 * Sie werden auf die Job-Parameter usage, usage_2, usage_3,...
	 * verteilt. Wenn zwischendrin welche fehlen, werden die hinteren
	 * nach vorn geschoben.
	 * @param t der Auftrag.
	 * @throws RemoteException
	 */
	protected void setJobParamUsage(Transfer t) throws RemoteException
	{
	  if (t == null)
	    return;
	  
	  String[] lines = VerwendungszweckUtil.toArray(t);
	  for (int i=0;i<lines.length;++i)
	  {
	    setJobParam(HBCIUtilsInternal.withCounter("usage",i),lines[i]);
	  }
	}
  
  /**
   * Legt fest, ob der HBCI-Job exclusive (also in einer einzelnen HBCI-Nachricht) gesendet werden soll.
   * Standardmaessig ist ein Job nicht exclusiv.
   * @return true, wenn er exclusiv gesendet werden soll.
   */
  public boolean isExclusive()
  {
    return this.exclusive;
  }
  
  /**
   * Legt fest, ob der HBCI-Job exclusive (also in einer einzelnen HBCI-Nachricht) gesendet werden soll.
   * Standardmaessig ist ein Job nicht exclusiv.
   * @param exclusive
   */
  public void setExclusive(boolean exclusive)
  {
    this.exclusive = exclusive;
  }
}
