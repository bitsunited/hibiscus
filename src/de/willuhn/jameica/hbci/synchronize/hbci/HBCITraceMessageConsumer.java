/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.hbci.synchronize.hbci;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.willuhn.annotation.Lifecycle;
import de.willuhn.annotation.Lifecycle.Type;
import de.willuhn.jameica.messaging.Message;
import de.willuhn.jameica.messaging.MessageConsumer;
import de.willuhn.logging.Logger;
import de.willuhn.util.History;

/**
 * Empfaengt HBCI-Trace-Messages.
 */
@Lifecycle(Type.CONTEXT) // Noetig, damit wir an die selbe Instanz kommen, die der MessageService verwendet
public class HBCITraceMessageConsumer implements MessageConsumer
{
  private Map<String,History> history = new HashMap<String,History>();
  private History current = null;
  
  /**
   * @see de.willuhn.jameica.messaging.MessageConsumer#getExpectedMessageTypes()
   */
  public Class[] getExpectedMessageTypes()
  {
    return new Class[]{HBCITraceMessage.class};
  }

  /**
   * @see de.willuhn.jameica.messaging.MessageConsumer#handleMessage(de.willuhn.jameica.messaging.Message)
   */
  public void handleMessage(Message message) throws Exception
  {
    HBCITraceMessage msg = (HBCITraceMessage) message;
    
    if (msg.getType() == HBCITraceMessage.Type.ID)
    {
      current = history.get(msg.getData());
      if (current == null)
      {
        current = new History(100);
        history.put(msg.getData(),current);
      }
      
      return;
    }
    
    if (current == null)
    {
      Logger.debug("no ID to assign HBCI trace to");
      return;
    }
    
    current.push(msg);
  }
  
  /**
   * Liefert den HBCI-Trace zur angegebenen ID.
   * @param id die ID. Typischerweise die des Konto.
   * @return der HBCI-Trace.
   */
  public List<HBCITraceMessage> getTrace(String id)
  {
    History h = this.history.get(id);
    List<HBCITraceMessage> list = new ArrayList<HBCITraceMessage>();
    
    if (h != null)
      list.addAll(h.elements());
    
    return list;
  }

  /**
   * @see de.willuhn.jameica.messaging.MessageConsumer#autoRegister()
   */
  public boolean autoRegister()
  {
    return false; // per Manifest
  }
}


