/*

Copyright (c) 2021, Richard Cane
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/
package com.interactivechat;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import net.runelite.client.util.LinkBrowser;

import okhttp3.HttpUrl;

public class Match {
    static final HttpUrl WIKI_BASE = HttpUrl.parse("https://oldschool.runescape.wiki");
    
    final int index;
    final String term;
    final Rectangle bounds;

    Match(int index, String term, int x, int y, int width) {
      this.bounds = new Rectangle(x, y + 4, width, InteractiveChat.CHATLINE_HEIGHT);
      this.index = index;
      this.term = term;
    }

    public MouseEvent onClick(MouseEvent e) {
      LinkBrowser.browse(WIKI_BASE.newBuilder().addQueryParameter("search", term).build().toString());

      e.consume();
      return e;
    }
  }