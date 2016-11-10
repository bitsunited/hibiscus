/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/
package de.willuhn.jameica.hbci.gui.views;

import java.rmi.RemoteException;

import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import de.willuhn.jameica.gui.AbstractView;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.internal.parts.PanelButtonPrint;
import de.willuhn.jameica.hbci.HBCI;
import de.willuhn.jameica.hbci.io.print.PrintSupportUmsatzList;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.system.Application;
import de.willuhn.util.I18N;

/**
 * Zeigt Kontoausz�ge an und gibt gibt sie in eine PDF-Datei aus.
 */
public class KontoauszugList extends AbstractView
{
  private final static I18N i18n = Application.getPluginLoader().getPlugin(HBCI.class).getResources().getI18N();

  /**
   * @see de.willuhn.jameica.gui.AbstractView#bind()
   */
  public void bind() throws Exception
  {
    GUI.getView().setTitle(i18n.tr("Ums�tze"));

    final de.willuhn.jameica.hbci.gui.parts.KontoauszugList list = new de.willuhn.jameica.hbci.gui.parts.KontoauszugList();
    
    // Konto vorauswaehlen, wenn es als Context uebergeben wurde
    Konto konto = this.getKonto();
    if (konto != null)
      list.getKontoAuswahl().setValue(konto);
    
    final PanelButtonPrint print = new PanelButtonPrint(new PrintSupportUmsatzList(list));
    list.addSelectionListener(new Listener() {
      public void handleEvent(Event event)
      {
        print.setEnabled(list.getSelection() != null);
      }
    });

    GUI.getView().addPanelButton(print);
    
    list.paint(getParent());
    print.setEnabled(list.getSelection() != null); // einmal initial ausloesen
  }
  
  /**
   * Liefert das Konto, insofern es der View als Context mitgegeben wurde.
   * @return das Konto oder NULL.
   * @throws RemoteException
   */
  private Konto getKonto() throws RemoteException
  {
    Object o = this.getCurrentObject();
    return (o instanceof Konto) ? (Konto) o : null;
  }

}
