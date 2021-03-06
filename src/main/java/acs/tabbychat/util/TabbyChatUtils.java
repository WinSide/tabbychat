package acs.tabbychat.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSleepMP;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import org.apache.commons.compress.compressors.gzip.GzipUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import acs.tabbychat.api.TCExtensionManager;
import acs.tabbychat.compat.MacroKeybindCompat;
import acs.tabbychat.core.ChatChannel;
import acs.tabbychat.core.GuiChatTC;
import acs.tabbychat.core.GuiNewChatTC;
import acs.tabbychat.core.GuiSleepTC;
import acs.tabbychat.core.TCChatLine;
import acs.tabbychat.core.TabbyChat;
import acs.tabbychat.gui.ITCSettingsGUI;
import acs.tabbychat.gui.context.ChatContextMenu;
import acs.tabbychat.gui.context.ContextCopy;
import acs.tabbychat.gui.context.ContextCut;
import acs.tabbychat.gui.context.ContextPaste;
import acs.tabbychat.gui.context.ContextSpellingSuggestion;
import acs.tabbychat.settings.ChannelDelimEnum;
import acs.tabbychat.settings.ColorCodeEnum;
import acs.tabbychat.settings.FormatCodeEnum;
import acs.tabbychat.settings.NotificationSoundEnum;
import acs.tabbychat.settings.TimeStampEnum;
import acs.tabbychat.threads.BackgroundChatThread;

import com.google.common.collect.Lists;
import com.mumfrey.liteloader.core.LiteLoader;

public class TabbyChatUtils {
    private static Calendar logDay = Calendar.getInstance();
    private static File logDir = new File(new File(Minecraft.getMinecraft().mcDataDir, "logs"),
            "TabbyChat");
    private static SimpleDateFormat logNameFormat = new SimpleDateFormat("'_'yyyy-MM-dd'.log'");
    public final static String version = "@@VERSION@@";
    public final static String name = "TabbyChat";
    public final static String modid = "tabbychat";
    public static Logger log = LogManager.getLogger(name);

    public static void startup() {
        // check if forge is installed.
        try {
            Class.forName("net.minecraftforge.common.MinecraftForge");
            TabbyChat.forgePresent = true;
            log.info("MinecraftForge detected.  Will check for client-commands.");
        } catch (ClassNotFoundException e) {
            TabbyChat.forgePresent = false;
        }

        compressLogs();

        ChatContextMenu.addContext(new ContextSpellingSuggestion());
        ChatContextMenu.addContext(new ContextCut());
        ChatContextMenu.addContext(new ContextCopy());
        ChatContextMenu.addContext(new ContextPaste());

        TCExtensionManager.INSTANCE.registerExtension(MacroKeybindCompat.class);

    }

    private static void compressLogs() {
        if (!logDir.exists())
            return;
        Collection<File> logs = FileUtils.listFiles(logDir, new String[] { "txt", "log" }, true);
        for (File file : logs) {
            String name = file.getName();
            if (name.contains(logNameFormat.format(logDay.getTime())))
                continue; // This is today's log.
            try {
                gzipFile(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void gzipFile(File file) throws IOException {
        File dest = new File(file.getParentFile(), GzipUtils.getCompressedFilename(file.getName()));
        FileOutputStream os = new FileOutputStream(dest);
        try {
            OutputStreamWriter writer = new OutputStreamWriter(new GZIPOutputStream(os), "UTF-8");
            try {
                writer.write(FileUtils.readFileToString(file));
            } finally {
                writer.close();
                file.delete();
            }
        } finally {
            os.close();
        }
    }

    public static void chatGuiTick(Minecraft mc) {
        GuiScreen screen = mc.currentScreen;
        if (screen == null)
            return;
        if (!(screen instanceof GuiChat))
            return;
        if (screen.getClass() == GuiChatTC.class)
            return;
        if (screen.getClass() == GuiSleepTC.class)
            return;

        String inputBuffer = "";
        try {
            int ind = 0;
            for (Field fields : GuiChat.class.getDeclaredFields()) {
                if (fields.getType() == String.class) {
                    if (ind == 1) {
                        fields.setAccessible(true);
                        inputBuffer = (String) fields.get(mc.currentScreen);
                        break;
                    }
                    ind++;
                }
            }
        } catch (Exception e) {
            TabbyChat.printException("Unable to display chat interface", e);
        }
        if (screen instanceof GuiSleepMP)
            mc.displayGuiScreen(new GuiSleepTC());
        else
            mc.displayGuiScreen(new GuiChatTC(inputBuffer));
    }

    public static ComponentList chatLinesToComponent(List<TCChatLine> lines) {
        ComponentList result = ComponentList.newInstance();
        for (TCChatLine line : lines) {
            result.add(line.getChatLineString());
        }
        return result;
    }

    public static ServerData getServerData() {
        Minecraft mc = Minecraft.getMinecraft();
        ServerData serverData = null;
        for (Field field : Minecraft.class.getDeclaredFields()) {
            if (field.getType() == ServerData.class) {
                field.setAccessible(true);
                try {
                    serverData = (ServerData) field.get(mc);
                } catch (Exception e) {
                    TabbyChat.printException("Unable to find server information", e);
                }
                break;
            }
        }
        return serverData;
    }

    public static File getServerDir() {
        String ip = new IPResolver(getServerIp()).getSafeAddress();
        return new File(ITCSettingsGUI.tabbyChatDir, ip);
    }

    /**
     * Returns the IP of the current server.
     */
    public static String getServerIp() {
        String ip;
        if (Minecraft.getMinecraft().isSingleplayer()) {
            ip = "singleplayer";
        } else if (getServerData() == null) {
            ip = "unknown";
        } else {
            ip = getServerData().serverIP;
        }
        return ip;
    }

    /**
     * Returns the directory the the configs are stored.
     */
    public static File getTabbyChatDir() {
        if (TabbyChat.liteLoaded) {
            return new File(LiteLoader.getCommonConfigFolder(), "tabbychat");
        } else {
            return new File(new File(Minecraft.getMinecraft().mcDataDir, "config"), "tabbychat");
        }
    }

    @SuppressWarnings("unchecked")
    public static void hookIntoChat(GuiNewChatTC _gnc) {
        if (Minecraft.getMinecraft().ingameGUI.getChatGUI().getClass() != GuiNewChatTC.class) {
            try {
                Class<GuiIngame> IngameGui = GuiIngame.class;
                Field persistantGuiField = IngameGui.getDeclaredFields()[6];
                persistantGuiField.setAccessible(true);
                persistantGuiField.set(Minecraft.getMinecraft().ingameGUI, _gnc);

                int tmp = 0;
                for (Field fields : GuiNewChat.class.getDeclaredFields()) {
                    if (fields.getType() == List.class) {
                        fields.setAccessible(true);
                        if (tmp == 0) {
                            _gnc.sentMessages = (List<String>) fields.get(_gnc);
                        } else if (tmp == 1) {
                            _gnc.backupLines = (List<TCChatLine>) fields.get(_gnc);
                        } else if (tmp == 2) {
                            _gnc.chatLines = (List<TCChatLine>) fields.get(_gnc);
                            break;
                        }
                        tmp++;
                    }
                }
            } catch (Exception e) {
                TabbyChat.printException("Error loading chat hook.", e);
            }
        }
    }

    /**
     * Logs chat.
     */
    public static void logChat(String theChat, ChatChannel theChannel) {
        Calendar tmpcal = Calendar.getInstance();
        File fileDir;
        String basename;
        // If the channel or title is null, create a new one.
        if (theChannel == null || theChannel.getTitle() == null) {
            theChannel = new ChatChannel("*");
        }
        // Set log file directory.
        if (getServerIp() == "singleplayer") {
            IntegratedServer ms = Minecraft.getMinecraft().getIntegratedServer();
            fileDir = new File(new File(logDir, "singleplayer"), ms.getWorldName());
        } else {
            fileDir = new File(logDir, new IPResolver(getServerIp()).getSafeAddress());
        }
        if (!theChannel.getTitle().equals("*")) {
            fileDir = new File(fileDir, theChannel.getTitle());
            basename = theChannel.getTitle();
        } else {
            basename = "all";
        }
        // Set log file
        if (theChannel.getLogFile() == null
                || tmpcal.get(Calendar.DAY_OF_YEAR) != logDay.get(Calendar.DAY_OF_YEAR)) {
            logDay = tmpcal;
            theChannel.setLogFile(new File(fileDir, basename
                    + logNameFormat.format(logDay.getTime())));
        }
        // Create the file
        if (!theChannel.getLogFile().exists()) {
            try {
                fileDir.mkdirs();
                theChannel.getLogFile().createNewFile();
            } catch (IOException e) {
                TabbyChat.printErr("Cannot create log file : '" + e.getLocalizedMessage() + "' : "
                        + e.toString());
                return;
            }
        }
        // If all good, log it
        try {
            FileUtils.writeLines(theChannel.getLogFile(), "UTF-8",
                    Lists.newArrayList(theChat.trim()), true);
        } catch (IOException e) {
            TabbyChat.printErr("Cannot write to log file : '" + e.getLocalizedMessage() + "' : "
                    + e.toString());
            return;
        }
    }

    public static Float median(float val1, float val2, float val3) {
        if (val1 < val2 && val1 < val3)
            return Math.min(val2, val3);
        else if (val1 > val2 && val1 > val3)
            return Math.max(val2, val3);
        else
            return val1;
    }

    public static ColorCodeEnum parseColor(Object _input) {
        if (_input == null)
            return null;
        String input = _input.toString();
        try {
            return ColorCodeEnum.valueOf(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static ChannelDelimEnum parseDelimiters(Object _input) {
        if (_input == null)
            return null;
        String input = _input.toString();
        try {
            return ChannelDelimEnum.valueOf(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static FormatCodeEnum parseFormat(Object _input) {
        if (_input == null)
            return null;
        String input = _input.toString();
        try {
            return FormatCodeEnum.valueOf(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Integer parseInteger(String _input, int min, int max, int fallback) {
        Integer result;
        try {
            result = Integer.parseInt(_input);
            result = Math.max(min, result);
            result = Math.min(max, result);
        } catch (NumberFormatException e) {
            result = fallback;
        }
        return result;
    }

    public static int parseInteger(String _input) {
        NumberFormat formatter = NumberFormat.getInstance();
        boolean state = formatter.isParseIntegerOnly();
        formatter.setParseIntegerOnly(true);
        ParsePosition pos = new ParsePosition(0);
        int result = formatter.parse(_input, pos).intValue();
        formatter.setParseIntegerOnly(state);
        if (_input.length() == pos.getIndex())
            return result;
        else
            return -1;
    }

    public static NotificationSoundEnum parseSound(Object _input) {
        if (_input == null)
            return NotificationSoundEnum.ORB;
        String input = _input.toString();
        try {
            return NotificationSoundEnum.valueOf(input);
        } catch (IllegalArgumentException e) {
            return NotificationSoundEnum.ORB;
        }
    }

    public static String parseString(Object _input) {
        if (_input == null)
            return " ";
        else
            return _input.toString();
    }

    public static TimeStampEnum parseTimestamp(Object _input) {
        if (_input == null)
            return null;
        String input = _input.toString();
        try {
            return TimeStampEnum.valueOf(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static List<TCChatLine> componentToChatLines(int stamp, ComponentList filtered, int id,
            boolean status) {
        List<TCChatLine> result = Lists.newArrayList();
        boolean first = true;
        for (IChatComponent split : filtered) {
            if (first) {
                result.add(new TCChatLine(stamp, split, id, status));
                first = false;
            } else
                result.add(new TCChatLine(stamp, new ChatComponentText(" ").appendSibling(split),
                        id, status));
        }
        return result;
    }

    public static LinkedHashMap<String, ChatChannel> swapChannels(
            LinkedHashMap<String, ChatChannel> currentMap, int _left, int _right) {
        // Ensure ordering of 'indices' is 0<=_left<_right<=end
        if (_left == _right)
            return currentMap;
        else if (_left > _right) {
            int _tmp = _left;
            _left = _right;
            _right = _tmp;
        }
        if (_right >= currentMap.size())
            return currentMap;

        // Convert map to array for access by index
        String[] arrayCopy = new String[currentMap.size()];
        arrayCopy = currentMap.keySet().toArray(arrayCopy);
        // Swap array entries using passed index arguments
        String tmp = arrayCopy[_left];
        arrayCopy[_left] = arrayCopy[_right];
        arrayCopy[_right] = tmp;
        // Create new map and populate
        int n = arrayCopy.length;
        LinkedHashMap<String, ChatChannel> returnMap = new LinkedHashMap<String, ChatChannel>(n);
        for (int i = 0; i < n; i++) {
            returnMap.put(arrayCopy[i], currentMap.get(arrayCopy[i]));
        }
        return returnMap;
    }

    public static void writeLargeChat(String toSend) {
        List<String> actives = TabbyChat.getInstance().getActive();
        BackgroundChatThread sendProc;
        if (!TabbyChat.getInstance().enabled() || actives.size() != 1)
            sendProc = new BackgroundChatThread(toSend);
        else {
            ChatChannel active = TabbyChat.getInstance().channelMap.get(actives.get(0));
            String tabPrefix = active.cmdPrefix;
            boolean hiddenPrefix = active.hidePrefix;

            if (TabbyChat.advancedSettings.convertUnicodeText.getValue()) {
                toSend = convertUnicode(toSend);
            }

            if (tabPrefix != null && tabPrefix.length() > 0) {
                if (!hiddenPrefix)
                    sendProc = new BackgroundChatThread(toSend, tabPrefix);
                else if (!toSend.startsWith("/"))
                    sendProc = new BackgroundChatThread(tabPrefix + " " + toSend, tabPrefix);
                else
                    sendProc = new BackgroundChatThread(toSend);
            } else
                sendProc = new BackgroundChatThread(toSend);
        }
        sendProc.start();
    }

    /**
     * Converts strings to unicode. Essentially replaces \\uabcd with \uabcd.
     * 
     * @param chat
     * @return
     */
    public static String convertUnicode(String chat) {
        String newChat = "";
        for (String s : chat.split("\\\u0000")) {
            if (s.contains("u")) {
                try {
                    newChat += StringEscapeUtils.unescapeJava(s);
                } catch (IllegalArgumentException e) {
                    newChat += s;
                }
            } else
                newChat += s;
        }
        return newChat;
    }
}
