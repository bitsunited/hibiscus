/**********************************************************************
 * $Source: /cvsroot/hibiscus/hibiscus/src/de/willuhn/jameica/hbci/gui/chart/AbstractChart.java,v $
 * $Revision: 1.9 $
 * $Date: 2011/05/04 10:38:40 $
 * $Author: willuhn $
 * $Locker:  $
 * $State: Exp $
 *
 * Copyright (c) by willuhn.webdesign
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.hbci.gui.chart;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.swtchart.ISeries;
import org.swtchart.ISeriesSet;
import org.swtchart.ext.Messages;
import org.swtchart.internal.Legend;

import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.hbci.HBCI;
import de.willuhn.jameica.system.Application;
import de.willuhn.util.I18N;

/**
 * Abstrakte Basis-Implementierung der Charts.
 * @param <T> der Typ der Chartdaten.
 */
public abstract class AbstractChart<T extends ChartData> implements Chart<T>
{
  final static I18N i18n = Application.getPluginLoader().getPlugin(HBCI.class).getResources().getI18N();

  private String title             = null;
  private Map<RGB,Color> colors    = new HashMap<RGB,Color>();
  private List<T> data             = new ArrayList<T>();
  private org.swtchart.Chart chart = null;

  /**
   * @see de.willuhn.jameica.hbci.gui.chart.Chart#setTitle(java.lang.String)
   */
  public void setTitle(String title)
  {
    this.title = title;
    if (this.chart != null && !this.chart.isDisposed())
      this.chart.getTitle().setText(this.title);
  }

  /**
   * @see de.willuhn.jameica.hbci.gui.chart.Chart#getTitle()
   */
  public String getTitle()
  {
    return this.title;
  }

  /**
   * Liefert das eigentliche SWT-Chart-Objekt.
   * @return das eigentliche SWT-Chart-Objekt.
   */
  protected org.swtchart.Chart getChart()
  {
    return this.chart;
  }

  /**
   * Speichert das SWT-Chart-Objekt.
   * @param chart
   */
  protected void setChart(final org.swtchart.Chart chart)
  {
    this.chart = chart;
    if (this.chart == null)
      return;
    
    final Legend l = (Legend) chart.getLegend();
    l.addMouseListener(new MouseAdapter()
    {
      /**
       * Schaltet die Sichtbarkeit der Series bei Doppelklick um.
       * @see org.eclipse.swt.events.MouseAdapter#mouseDoubleClick(org.eclipse.swt.events.MouseEvent)
       */
      @Override
      public void mouseDoubleClick(MouseEvent e)
      {
        ISeries s = getSeries(chart, l, e.x, e.y);
        if (s != null)
        {
          // Sichtbarkeit umschalten
          s.setVisible(!s.isVisible());
          chart.redraw();
        }
      }
    });
    
    l.setMenu(createLegendContextMenu(chart, l));
  }

  /**
   * Erzeugt ein Contextmenu fuer die Legende.
   * @param chart das Chart-Objekt.
   * @param l die Legende.
   * @return das Contextmenu.
   */
  private Menu createLegendContextMenu(final org.swtchart.Chart chart, Legend l)
  {
    Menu m = new Menu(l.getParent().getShell(), SWT.POP_UP);
    addShowMenuItem(m, i18n.tr("alle anzeigen"), true);
    addShowMenuItem(m, i18n.tr("alle ausblenden"), false);
    return m;
  }

  /**
   * Erzeugt einen Menu-Eintrag.
   * @param m das zugehoerige Menu.
   * @param text der anzuzeigende Text.
   * @param setVisibleValue das Sichtbarkeitsflag, welches beim Klick auf den Menu-Eintrag ausgefuehrt werden soll.
   */
  private void addShowMenuItem(Menu m, String text, final boolean setVisibleValue)
  {
    MenuItem item = new MenuItem(m, SWT.PUSH);
    item.setText(text);
    item.addSelectionListener(new SelectionAdapter() {
      /**
       * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
       */
      @Override
      public void widgetSelected(SelectionEvent e)
      {
        ISeriesSet seriesSet = chart.getSeriesSet();
        for (ISeries s : seriesSet.getSeries())
        {
          s.setVisible(setVisibleValue);
        }
        chart.redraw();
      }
    });
  }

  /**
   * Liefert die Datenreihe fuer die angegebene Position.
   * @param chart das Chart.
   * @param l die Legende.
   * @param x die X-Position.
   * @param y die Y-Position.
   * @return
   */
  private ISeries getSeries(org.swtchart.Chart chart, Legend l, int x, int y)
  {
    ISeriesSet seriesSet = chart.getSeriesSet();
    for (ISeries s:seriesSet.getSeries())
    {
      Rectangle sbounds = l.getBounds(s.getId());
      if(sbounds.contains(x, y))
        return s;
    }
    return null;
  }

  /**
   * @see de.willuhn.jameica.hbci.gui.chart.Chart#addData(de.willuhn.jameica.hbci.gui.chart.ChartData)
   */
  public void addData(T data)
  {
    if (data != null)
      this.data.add(data);
  }

  /**
   * @see de.willuhn.jameica.hbci.gui.chart.Chart#removeData(de.willuhn.jameica.hbci.gui.chart.ChartData)
   */
  public void removeData(T data)
  {
    if (data != null)
      this.data.remove(data);
  }

  /**
   * @see de.willuhn.jameica.hbci.gui.chart.Chart#removeAllData()
   */
  public void removeAllData()
  {
    if (this.data != null)
      this.data.clear();
  }

  /**
   * Liefert die anzuzeigenden Daten.
   * @return Anzuzeigende Daten.
   */
  List<T> getData()
  {
    return this.data;
  }
  
  /**
   * Erzeugt eine Farbe, die automatisch disposed wird.
   * Die Funktion hat einen internen Cache. Wenn die Farbe schon im Cache
   * vorhanden ist, wird diese genommen.
   * @param rgb der Farbcode.
   * @return die Farbe.
   */
  Color getColor(RGB rgb)
  {
    // Schon im Cache?
    Color c = this.colors.get(rgb);
    if (c != null && !c.isDisposed())
      return c;
    
    c = new Color(GUI.getDisplay(),rgb);
    this.colors.put(rgb,c);
    return c;
  }
  
  /**
   * Entfernt den Menu-Eintrag "Properties" aus dem InteractiveChart,
   * weil das nur in Eclipse funktioniert. In Jameica wuerde das
   * eine Exception im Main-Loop ausloesen.
   */
  private void cleanMenu()
  {
    try
    {
      MenuItem[] items = this.chart.getPlotArea().getMenu().getItems();
      if (items == null || items.length == 0)
        return;

      for (int i=0;i<items.length;++i)
      {
        MenuItem mi = items[i];
        String text = mi.getText();
        if (text == null)
          continue;
        if (text.equals(Messages.PROPERTIES))
        {
          mi.dispose();
          
          // Den Separator davor gleich noch entfernen
          if (i > 0) items[i-1].dispose();
          return;
        }
      }
    }
    catch (Exception e)
    {
      // Dann halt nicht ;)
    }
  }
  

  /**
   * @see de.willuhn.jameica.gui.Part#paint(org.eclipse.swt.widgets.Composite)
   */
  public void paint(Composite parent) throws RemoteException
  {
    parent.addDisposeListener(new DisposeListener() {
      /**
       * @see org.eclipse.swt.events.DisposeListener#widgetDisposed(org.eclipse.swt.events.DisposeEvent)
       */
      public void widgetDisposed(DisposeEvent e)
      {
        try
        {
          Iterator<Color> i = colors.values().iterator();
          while (i.hasNext())
          {
            Color c = i.next();
            if (c != null && !c.isDisposed())
              c.dispose();
          }
        }
        finally
        {
          colors.clear();
        }
      }
    });
    cleanMenu();
  }
}

/*********************************************************************
 * $Log: AbstractChart.java,v $
 * Revision 1.9  2011/05/04 10:38:40  willuhn
 * @B Titel des Charts wurde bei Aenderung des Zeitraumes nicht aktualisiert
 *
 * Revision 1.8  2010-11-24 16:27:17  willuhn
 * @R Eclipse BIRT komplett rausgeworden. Diese unsaegliche Monster ;)
 * @N Stattdessen verwenden wir jetzt SWTChart (http://www.swtchart.org). Das ist statt den 6MB von BIRT sagenhafte 250k gross
 *
 **********************************************************************/