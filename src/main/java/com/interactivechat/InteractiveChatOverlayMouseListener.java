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

import java.awt.event.MouseEvent;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.input.MouseAdapter;

@Singleton
public class InteractiveChatOverlayMouseListener extends MouseAdapter {
	private final Client client;
	private MatchManager matchManager;

	@Inject
	private InteractiveChatOverlayMouseListener(Client client, MatchManager matchManager)
   {
      this.client = client;
      this.matchManager = matchManager;
   }

   @Override
   public MouseEvent mousePressed(MouseEvent e) {
      final List<Match> matches = matchManager.getMatches();
      if (!SwingUtilities.isLeftMouseButton(e) || matches.isEmpty()) {
         return e;
      }

      Point mouse = client.getMouseCanvasPosition();
      for (Match match : matches) {
         if (match.bounds.contains(mouse.getX(), mouse.getY())) {
            return match.onClick(e);
         }
      }

      return e;
   }
}