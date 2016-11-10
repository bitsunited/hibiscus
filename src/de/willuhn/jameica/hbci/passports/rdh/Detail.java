/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/
package de.willuhn.jameica.hbci.passports.rdh;

import de.willuhn.jameica.gui.AbstractView;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.ColumnLayout;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.Headline;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.jameica.hbci.HBCI;
import de.willuhn.jameica.hbci.HBCIProperties;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.OperationCanceledException;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;

/**
 * Detail-Ansicht eines Passports.
 */
public class Detail extends AbstractView
{
  private final static I18N i18n = Application.getPluginLoader().getPlugin(HBCI.class).getResources().getI18N();

  /**
   * @see de.willuhn.jameica.gui.AbstractView#bind()
   */
  public void bind() throws Exception
  {
    try
    {
      final Controller control = new Controller(this);

      GUI.getView().setTitle(i18n.tr("Schl�ssel-Details"));

      ColumnLayout layout = new ColumnLayout(getParent(),2);

      {
        Container group = new SimpleContainer(layout.getComposite());
        group.addHeadline(i18n.tr("Verbindungsdaten zur Bank"));
        group.addInput(control.getHBCIUrl());
        group.addInput(control.getHBCIPort());
        group.addInput(control.getHBCIVersion());
      }
      
      {
        Container group = new SimpleContainer(layout.getComposite());
        group.addHeadline(i18n.tr("Benutzerdaten"));
        group.addInput(control.getBenutzerkennung());
        group.addInput(control.getKundenkennung());
        group.addInput(control.getBLZ());
      }
      
      {
        Container group = new SimpleContainer(getParent());
        group.addHeadline(i18n.tr("Erweiterte Einstellungen"));
        group.addInput(control.getAlias()); // BUGZILLA 72
        group.addInput(control.getPath()); // BUGZILLA 148
      }

      {
        ButtonArea buttons = new ButtonArea();
        buttons.addButton(i18n.tr("Passwort �ndern"),new Action()
        {
          public void handleAction(Object context) throws ApplicationException
          {
            control.changePassword();
          }
        },null,false,"seahorse-preferences.png");
        buttons.addButton(i18n.tr("INI-Brief anzeigen/erzeugen"),new Action()
        {
          public void handleAction(Object context) throws ApplicationException
          {
            control.startIniLetter();
          }
        },null,false,"stock_keyring.png");
        buttons.paint(getParent());
      }

      
      new Headline(getParent(),i18n.tr("Fest zugeordnete Konten"));
      control.getKontoAuswahl().paint(getParent());

      ButtonArea buttons = new ButtonArea();
      buttons.addButton(i18n.tr("BPD/UPD"),new Action()
      {
        public void handleAction(Object context) throws ApplicationException
        {
          control.handleDisplayProperties();
        }
      },null,false,"text-x-generic.png");
      buttons.addButton(i18n.tr("Konfiguration testen"),new Action()
      {
        public void handleAction(Object context) throws ApplicationException
        {
          control.handleTest();
        }
      },null,false,"dialog-information.png");
      buttons.addButton(i18n.tr("Signatur-ID synchronisieren"),new Action()
      {
        public void handleAction(Object context) throws ApplicationException
        {
          control.syncSigId();
        }
      },null,false,"view-refresh.png");
      buttons.addButton(i18n.tr("Speichern"),new Action()
      {
        public void handleAction(Object context) throws ApplicationException
        {
          control.handleStore();
        }
      },null,false,"document-save.png");

      buttons.paint(getParent());
      
      // Ggf. angezeigten Fehlertext von vorher loeschen
      Application.getMessagingFactory().sendMessage(new StatusBarMessage("",StatusBarMessage.TYPE_SUCCESS));
    }
    catch (Exception e)
    {
      Throwable oce = HBCIProperties.getCause(e,OperationCanceledException.class);
      Throwable ae  = HBCIProperties.getCause(e,ApplicationException.class);

      if (oce != null)
      {
        Logger.info("operation cancelled by user: " + oce.getMessage());
      }
      else if (ae != null)
      {
        Application.getMessagingFactory().sendMessage(new StatusBarMessage(ae.getMessage(),StatusBarMessage.TYPE_ERROR));
      }
      else
      {
        Logger.error("unable to load key",e);
        Application.getMessagingFactory().sendMessage(new StatusBarMessage(i18n.tr("Fehler beim Laden des Schl�ssels: {0}",e.getMessage()),StatusBarMessage.TYPE_ERROR));
      }
      // Wir springen auf jeden Fall zurueck, zur vorherigen Seite. Wir koennten hier eh nichts anzeigen
      GUI.startPreviousView();
    }
  }
  
  /**
   * @see de.willuhn.jameica.gui.AbstractView#canBookmark()
   */
  public boolean canBookmark()
  {
    return false;
  }
}
