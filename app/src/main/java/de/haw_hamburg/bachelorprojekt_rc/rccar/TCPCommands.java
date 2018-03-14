package de.haw_hamburg.bachelorprojekt_rc.rccar;


/**
 * Created by rene on 06.03.18.
 */

public class TCPCommands {
    public static int TYPE_CMD = 1;
    public static int TYPE_FILE_CONTENT = 2;

    public static byte SERVER_REQUEST = 0x01;
    public static byte SERVER_READY =  0x11;
    public static byte SERVER_FINISHED = 0x12;
    public static byte SERVER_CONNECTION_CLOSED = 0x13;

    public static String CMD_REQUEST_FILES = "server_get_files";
    public static String CMD_REQUEST_FILES_RESPONSE = "server_get_files_response";
    public static String CMD_REQUEST_FILE_DOWNLOAD = "server_download_file";
}
