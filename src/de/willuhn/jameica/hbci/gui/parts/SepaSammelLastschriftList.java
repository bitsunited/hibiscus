/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.hbci.gui.parts;

import java.rmi.RemoteException;

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.Part;
import de.willuhn.jameica.hbci.rmi.SepaSammelLastschrift;

/**
 * Implementierung einer fix und fertig vorkonfigurierten Liste mit SEPA-Sammel-Lastschriften.
 */
public class SepaSammelLastschriftList extends AbstractSepaSammelTransferList implements Part
{

  /**
   * @param action
   * @throws RemoteException
   */
  public SepaSammelLastschriftList(Action action) throws RemoteException
  {
    super(action);
    addColumn(i18n.tr("Art"),"sepatype");
    addColumn(i18n.tr("Sequenz"),"sequencetype");
    setContextMenu(new de.willuhn.jameica.hbci.gui.menus.SepaSammelLastschriftList());
  }

  /**
   * @see de.willuhn.jameica.hbci.gui.parts.AbstractSammelTransferList#getObjectType()
   */
  protected Class getObjectType()
  {
    return SepaSammelLastschrift.class;
  }
}