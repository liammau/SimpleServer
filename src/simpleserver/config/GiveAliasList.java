/*******************************************************************************
 * Open Source Initiative OSI - The MIT License:Licensing
 * The MIT License
 * Copyright (c) 2010 Charles Wagner Jr. (spiegalpwns@gmail.com)
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 ******************************************************************************/
package simpleserver.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class GiveAliasList extends PropertiesConfig {
  private Map<String, Integer> aliases;

  public GiveAliasList() {
    super("give-aliases-list.txt");

    aliases = new HashMap<String, Integer>();
  }

  public Integer getId(String itemAlias) {
    return aliases.get(itemAlias);
  }

  @Override
  public void load() {
    super.load();

    aliases.clear();
    for (Entry<Object, Object> alias : entrySet()) {
      Integer id;
      try {
        id = Integer.valueOf((String) alias.getValue());
      }
      catch (NumberFormatException e) {
        System.out.println("Invalid give alias: " + alias.toString());
        continue;
      }

      aliases.put(((String) alias.getKey()).toLowerCase(), id);
    }
  }
}