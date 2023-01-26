package org.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.concurrent.CompletionStage;
import org.json.*;
import java.util.Date;
import java.sql.*;


public class Main {

    private static Connection connection = null;
    private static String webSocketConnectionString = "";

    public static void main(String[] args) throws InterruptedException {

        boolean DBConnected = DBConnect();

        if (!DBConnected) {
        } else {
            webSocketConnectionString = getWebSocketConnectionString();
            if (webSocketConnectionString != "") {
                try {
                    WebSocketConnect(webSocketConnectionString);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static void WebSocketConnect(String webSocketConnectionString) throws InterruptedException {
        try {
            WebSocket ws = HttpClient
                    .newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create(webSocketConnectionString), new WebSocketClient())
                    .join();
            while (true) {
            }
        } catch (Exception e) {
            System.out.println("Unable to connect to WebSocket Server: " + webSocketConnectionString);
        } finally {
            Thread.sleep(1000);
            WebSocketConnect(webSocketConnectionString);
        }
    }

    private static String getWebSocketConnectionString() {
        String URI = "";
        int port = 0;
        String rootPass = "";

        try {
            CallableStatement cs = connection.prepareCall("{call LWFWebsocketConnectionString_StoredProcedure(?, ?, ?, ?)}");
            cs.setString(1, "Tirana");
            cs.registerOutParameter(2, Types.VARCHAR);
            cs.registerOutParameter(3, Types.INTEGER);
            cs.registerOutParameter(4, Types.VARCHAR);
            cs.execute();
            URI = cs.getString(2);
            port = cs.getInt(3);
            rootPass = cs.getString(4);
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                System.out.println(e);
            }
        }
        return (URI + ":" + Integer.toString(port) + rootPass);
    }

    public static boolean DBConnect() {
        //Database//
        String url = "jdbc:sqlserver://lwf-sql-server-west-europe.database.windows.net;databaseName=BacsoftServerLWF";
        String user = "Bacsoft";
        String password = "SnowWhite1234!!";

        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            DriverManager.registerDriver(new com.microsoft.sqlserver.jdbc.SQLServerDriver());
            connection = DriverManager.getConnection(url, user, password);
            return true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static class WebSocketClient implements WebSocket.Listener {

        public void onOpen(WebSocket webSocket) {
            System.out.println("onOpen using sub protocol " + webSocket.getSubprotocol());
            WebSocket.Listener.super.onOpen(webSocket);
        }

        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            String uniqId = data.toString();
            try {
                JSONObject jsonData = new JSONObject(uniqId);
                String type = GetAlertType(jsonData);
                if (type.equals("Register")) {
                    SendFilter(webSocket, uniqId);
                }
                if (type.equals("Alert")) {
                    ParseAlertMessage(jsonData);
                }
            } catch (JSONException e) {
                System.out.println("Wrong Alert");
            }
            System.out.println("onText received: " + data);
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        private static String GetAlertType(JSONObject jsonData) {
            return jsonData.getString("object_type");
        }

        public static void SendFilter(WebSocket webSocket, String data) {
            String[] str = data.split(":");
            String temp = str[4];
            String unique_id = temp.substring(0, temp.length() - 1);
            long myLong = Long.parseLong(unique_id);
            String objectType = "Filter";
            long connectionId = myLong;
            String[] objectTypeFilter = {"Event", "Alert"};
            String jsonStr = String.format("{\"object_type_version\":1,\"object_type\":\"%s\",\"connection_id\":%d,\"object_type_filter\":[\"%s\",\"%s\"]}", objectType, connectionId, objectTypeFilter[0], objectTypeFilter[1]);
            webSocket.sendText(jsonStr, true);
        }

        public static void ParseAlertMessage(JSONObject jsonData) {
            int AlertID = jsonData.getInt("id");
            JSONObject jsonDataInternal = jsonData.getJSONObject("internal");
            JSONObject jsonDataDetails = jsonDataInternal.getJSONObject("details");
            int SerialNum = jsonDataDetails.getInt("fibre_line_id");
            String AlertType = jsonData.getString("alert_type");
            Color color = Color.valueOf(jsonDataInternal.getString("threat_level"));
            int ThreatLevel = color.getVal();
            SimpleDateFormat formatter = new SimpleDateFormat(jsonData.getString("time").replace("T", " ").replace("Z", ""));
            Date date = new Date();
            String ArrivalTime = formatter.format(date);
            float Latitude = jsonDataInternal.getFloat("latitude");
            float Longitude = jsonDataInternal.getFloat("longitude");
            int ResolvedFlag = jsonData.getInt("resolved_flag");

            try {

                System.out.println("We are in database");
                CallableStatement cs = connection.prepareCall("{call HandlePipelineAlert(?,?,?,?,?,?,?,?)}");
                cs.setInt(1, AlertID);
                cs.setInt(2, SerialNum);
                cs.setString(3, AlertType);
                cs.setInt(4, ThreatLevel);
                cs.setString(5, ArrivalTime);
                cs.setFloat(6, Latitude);
                cs.setFloat(7, Longitude);
                cs.setInt(8, ResolvedFlag);
                cs.executeUpdate();

            } catch (SQLException e) {
                System.out.println(e);
            }
        }

        public void onError(WebSocket webSocket, Throwable error) {
            System.out.println("Bad day! " + webSocket.toString());
            System.out.println("Error==>" + error);
            if (webSocketConnectionString != "") {
                try {
                    WebSocketConnect(webSocketConnectionString);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            WebSocket.Listener.super.onError(webSocket, error);
        }
    }
}

