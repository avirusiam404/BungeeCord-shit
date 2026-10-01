package net.md_5.bungee;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.connection.LoginResult;

public class Bootstrap
{

    public static void main(String[] args) throws Exception
    {
        // ---- UUID mode handling ----
        // args[0] can be:
        //   "offline" -> offline mode, plain offline UUIDs (no Mojang lookup)
        //   "online"  -> offline mode, but resolve the real Mojang UUID per player
        //   "auto"    -> use config.yml (default, same as "online")
        //   anything else -> treated as a username whose UUID is forced for all offline players
        //
        // NOTE: neither "offline" nor "online" enables real online-mode auth.
        //       Both run the proxy with online_mode = false.
        if ( args.length >= 1 )
        {
            String mode = args[0];

            if ( "offline".equalsIgnoreCase( mode ) )
            {
                InitialHandler.forcedUuidMode = InitialHandler.UuidMode.OFFLINE;
                System.out.println( "[BungeeCord] UUID mode: offline (plain offline UUIDs, no Mojang lookup)." );
            } else if ( "online".equalsIgnoreCase( mode ) )
            {
                InitialHandler.forcedUuidMode = InitialHandler.UuidMode.ONLINE;
                System.out.println( "[BungeeCord] UUID mode: online-spoof (real Mojang UUIDs, offline auth)." );
            } else if ( "auto".equalsIgnoreCase( mode ) )
            {
                System.out.println( "[BungeeCord] UUID mode: auto (using config.yml)." );
            } else
            {
                // Backwards-compatible: treat as a username whose UUID is forced for everyone.
                try
                {
                    UUID resolved = lookupUuidBlocking( mode );
                    if ( resolved != null )
                    {
                        InitialHandler.forcedUuid = resolved;
                        System.out.println( "[BungeeCord] Forcing UUID " + resolved
                                + " (from '" + mode + "') for all offline-mode players." );
                    } else
                    {
                        System.out.println( "[BungeeCord] Could not resolve UUID for '"
                                + mode + "', falling back to auto mode." );
                    }
                } catch ( Exception ex )
                {
                    System.out.println( "[BungeeCord] Failed to resolve UUID for '"
                            + mode + "': " + ex.getMessage() );
                }
            }
        } else
        {
            System.out.println( "[BungeeCord] UUID mode: auto (no argument given)." );
        }
        // ---- end UUID mode handling ----

        System.setProperty( "java.net.preferIPv4Stack", "true" );
        System.setProperty( "jline.terminal", "jline.UnsupportedTerminal" );

        BungeeCord bungee = new BungeeCord();
        net.md_5.bungee.api.ProxyServer.setInstance( bungee );
        bungee.getLogger().info( "Enabled BungeeCord version " + bungee.getVersion() );

        try
        {
            bungee.start();
        } catch ( Exception ex )
        {
            bungee.getLogger().log( java.util.logging.Level.SEVERE, "Critical error during startup. Aborting", ex );
            return;
        }

        if ( !bungee.isRunning )
        {
            bungee.getLogger().info( "No modules found, exiting." );
            bungee.stop();
            return;
        }

        while ( bungee.isRunning )
        {
            String line;
            try
            {
                line = bungee.getConsoleReader().readLine( ">" );
            } catch ( org.jline.reader.UserInterruptException ex )
            {
                // Ctrl+C at the console prompt -> shut down cleanly
                bungee.stop();
                break;
            } catch ( org.jline.reader.EndOfFileException ex )
            {
                // Ctrl+D -> shut down cleanly
                bungee.stop();
                break;
            }

            if ( line != null )
            {
                if ( !bungee.getPluginManager().dispatchCommand( net.md_5.bungee.command.ConsoleCommandSender.getInstance(), line ) )
                {
                    bungee.getConsole().sendMessage(
                            net.md_5.bungee.api.chat.TextComponent.fromLegacy( "Command not found" ) );
                }
            }
        }
    }

    /**
     * Resolves a username to a Mojang UUID synchronously. Called once at
     * startup when the user passes a username as the first CLI argument.
     */
    private static UUID lookupUuidBlocking(String username) throws Exception
    {
        URL url = new URL( "https://api.mojang.com/users/profiles/minecraft/"
                + URLEncoder.encode( username, "UTF-8" ) );
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout( 5000 );
        conn.setReadTimeout( 5000 );

        if ( conn.getResponseCode() != 200 )
        {
            return null;
        }

        try ( InputStreamReader reader = new InputStreamReader( conn.getInputStream(), StandardCharsets.UTF_8 ) )
        {
            LoginResult obj = LoginResult.GSON.fromJson( reader, LoginResult.class );
            if ( obj != null && obj.getId() != null )
            {
                return Util.getUUID( obj.getId() );
            }
        }
        return null;
    }
}
