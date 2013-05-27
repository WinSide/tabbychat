package acs.tabbychat.threads;

import java.util.ArrayList;

import acs.tabbychat.core.ChatChannel;
import acs.tabbychat.core.TCChatLine;
import acs.tabbychat.core.TabbyChat;
import acs.tabbychat.util.TabbyChatUtils;
import net.minecraft.client.Minecraft;

public class BackgroundUpdateCheck extends Thread {
	private static String newest = "";
	
	public BackgroundUpdateCheck(String _newest) {
		newest = _newest;
	}
	
	public void run() {
		if(!TabbyChat.generalSettings.tabbyChatEnable.getValue()) return;
		Minecraft mc = Minecraft.getMinecraft();
		String ver;
		ArrayList<TCChatLine> updateMsg = new ArrayList<TCChatLine>();
		if (!newest.equals(TabbyChatUtils.version)) {
			ver = "\u00A77TabbyChat: An update is available!  (Current version is "+TabbyChatUtils.version+", newest is "+newest+")";
			String ver2 = " \u00A77Visit the TabbyChat forum thread at minecraftforum.net to download.";
			TCChatLine updateLine = new TCChatLine(mc.ingameGUI.getUpdateCounter(), ver, 0, true);
			TCChatLine updateLine2 = new TCChatLine(mc.ingameGUI.getUpdateCounter(), ver2, 0, true);
			if(!TabbyChat.instance.channelMap.containsKey("TabbyChat")) TabbyChat.instance.channelMap.put("TabbyChat", new ChatChannel("TabbyChat"));
			updateMsg.add(updateLine);
			updateMsg.add(updateLine2);			
			//TabbyChat.instance.processChat(updateMsg);
			//TabbyChat.instance.addLastChatToChannel(TabbyChat.instance.channelMap.get("TabbyChat"));
			TabbyChat.instance.addToChannel("*", updateMsg);
			TabbyChat.instance.addToChannel("TabbyChat", updateMsg);
		}
	}	
}