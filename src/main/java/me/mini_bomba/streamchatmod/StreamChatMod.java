package me.mini_bomba.streamchatmod;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.sun.net.httpserver.HttpServer;
import me.mini_bomba.streamchatmod.commands.TwitchChatCommand;
import me.mini_bomba.streamchatmod.commands.TwitchCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLModDisabledEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Mod(modid = StreamChatMod.MODID, version = StreamChatMod.VERSION, clientSideOnly = true)
public class StreamChatMod
{
    public static final String MODID = "streamchatmod";
    public static final String MODNAME = "StreamChat";
    public static final String VERSION = "1.1";
    private static final Logger LOGGER = LogManager.getLogger();
    public StreamConfig config;
    @Nullable
    public TwitchClient twitch = null;
    @Nullable
    public HttpServer httpServer = null;
    public int httpShutdownTimer = -1;

    private final StreamEvents events;

    public StreamChatMod() {
        events = new StreamEvents(this);
    }
    
    @EventHandler
    public void postInit(FMLPostInitializationEvent event)
    {
		startTwitch();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler commandHandler = ClientCommandHandler.instance;
        commandHandler.registerCommand(new TwitchChatCommand(this));
        commandHandler.registerCommand(new TwitchCommand(this));

        MinecraftForge.EVENT_BUS.register(events);
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModMetadata metadata = event.getModMetadata();
        metadata.autogenerated = false;
        metadata.name = MODNAME;
        metadata.authorList = Collections.singletonList("mini_bomba");
        metadata.description = "A Chat client for some streaming websites in minecraft beacuse yes.";
        metadata.version = VERSION;
        config = new StreamConfig(event.getSuggestedConfigurationFile());
    }

    @EventHandler
    public void stop(FMLModDisabledEvent event) {
        stopTwitch();
        config.saveIfChanged();
    }

    public void startTwitch() {
        if (twitch != null || !config.twitchEnabled.getBoolean()) return;
        String token = config.twitchToken.getString();
        if (token.equals("")) return;
        OAuth2Credential credential = new OAuth2Credential("twitch", token);
        twitch = TwitchClientBuilder.builder()
                .withDefaultAuthToken(credential)
                .withEnableChat(true)
                .withChatAccount(credential)
                .build();
        twitch.getEventManager().onEvent(ChannelMessageEvent.class, this::onTwitchMessage);
        TwitchChat chat = twitch.getChat();
        chat.connect();
        List<String> channels = Arrays.asList(config.twitchChannels.getStringList());
        for (String channel : chat.getChannels()) {
            if (!channels.contains(channel)) chat.leaveChannel(channel);
        }
        for (String channel : channels) {
            chat.joinChannel(channel);
        }
    }

    private void onTwitchMessage(ChannelMessageEvent event) {
        boolean showChannel = config.forceShowChannelName.getBoolean() ||(twitch != null && twitch.getChat().getChannels().size() > 1);
        sendLocalMessage(EnumChatFormatting.DARK_PURPLE+"[TWITCH"+(showChannel ? "/"+event.getChannel().getName() : "")+"]"+EnumChatFormatting.WHITE+" <"+event.getUser().getName()+"> "+event.getMessage());
        if (this.config.playSoundOnMessage.getBoolean()) Minecraft.getMinecraft().thePlayer.playSound("note.pling", 0.1f, 1.25f);
    }

    private void sendLocalMessage(IChatComponent chat) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;
        player.addChatMessage(chat);
    }

    private void sendLocalMessage(String msg) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;
        StreamUtils.addMessage(player, msg);
    }

    public void stopTwitch() {
        if (twitch == null) return;
        TwitchChat chat = twitch.getChat();
        for (String channel : chat.getChannels()) {
            chat.leaveChannel(channel);
        }
        twitch.close();
        this.twitch = null;
    }
}
