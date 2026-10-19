package local.tv.marketwatcher;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/** Dated local bullion history, kept separate from stock/exchange quotes. */
final class MetalHistory {
    static boolean isMetal(String id) { return id.startsWith("gold") || id.equals("silver"); }
    static final String SILVER_PAGE = "https://www.goodreturns.in/silver-rates/chennai.html";
    static String silverJson() throws Exception {
        String html = MarketData.get(SILVER_PAGE);
        Matcher token = Pattern.compile("var\\s+gr_api_token\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(html);
        if (!token.find()) throw new IOException("Silver chart access unavailable");
        // Public, short-lived page token. Never persist or log it.
        try { return silverGraph(token.group(1)); }
        catch (IOException expired) {
            // Same public token renewal used by the provider's own silver chart.
            HttpURLConnection connection=(HttpURLConnection)new URL("https://www.goodreturns.in/src/get_api_token.php").openConnection();
            connection.setConnectTimeout(9000);connection.setReadTimeout(12000);connection.setRequestMethod("POST");connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json");
            connection.setRequestProperty("User-Agent","Mozilla/5.0 (Android TV; MarketWatcher/1.0)");
            try {
                try(java.io.OutputStream out=connection.getOutputStream()){out.write("{\"module\":\"silver_rates\"}".getBytes(StandardCharsets.UTF_8));}
                if(connection.getResponseCode()!=200)throw new IOException("Silver source access unavailable");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream input=connection.getInputStream()){byte[] buffer=new byte[2048];int n;while((n=input.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>16384)throw new IOException("Invalid silver access response");}}
                JSONObject response=new JSONObject(bytes.toString("UTF-8"));
                if(!"success".equals(response.optString("status")))throw new IOException("Silver source access unavailable");
                return silverGraph(response.getJSONObject("data").getString("token"));
            } finally {connection.disconnect();}
        }
    }
    private static String silverGraph(String token)throws Exception {
        return MarketData.get("https://www.goodreturns.in/silver-graph-data.html?city=chennai&timeframe=1Y&token="+URLEncoder.encode(token,"UTF-8"));
    }
    static MarketData.Quote silver(String raw) throws Exception {
        JSONArray rows = new JSONArray(raw);
        TreeMap<Long,Double> points = new TreeMap<>();
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        date.setTimeZone(MarketData.IST); date.setLenient(false);
        for (int i=0; i<rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String day = row.getString("on_date");
            if (!day.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IOException("Invalid silver date");
            long t = date.parse(day).getTime();
            double kg = row.getDouble("y"), labelKg = MarketData.number(row.getString("silver_price_1kg"));
            if (!Double.isFinite(kg) || kg<=0 || Math.abs(kg-labelKg)>0.01
                    || t>System.currentTimeMillis()+86400000L) throw new IOException("Invalid silver history");
            points.put(t, kg/1000.0);
        }
        return quote("silver", "Chennai · Silver", "Goodreturns", points);
    }
    static String goldMonthUrl(int year, int month) {
        return "https://www.livechennai.com/get_goldrate_history.asp?monthno="+month+"&yearno="+year;
    }
    static void addGoldMonth(String html, int year, int month, TreeMap<Long,Double> q22,
            TreeMap<Long,Double> q24) throws Exception {
        Matcher rows = Pattern.compile("<tr\\b[^>]*>(.*?)</tr>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(html);
        SimpleDateFormat date = new SimpleDateFormat("dd/MMMM/yyyy",Locale.ENGLISH);
        date.setTimeZone(MarketData.IST); date.setLenient(false);
        int count=0;
        while(rows.find()) {
            Matcher cells=Pattern.compile("<td\\b[^>]*>(.*?)</td>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(rows.group(1));
            List<String> values=new ArrayList<>(); while(cells.find())values.add(MarketData.clean(cells.group(1)));
            if(values.size()!=3 || !values.get(0).matches("\\d{1,2}/[A-Za-z]+/\\d{4}"))continue;
            long t=date.parse(values.get(0)).getTime();
            Calendar actual=Calendar.getInstance(MarketData.IST);actual.setTimeInMillis(t);
            if(actual.get(Calendar.YEAR)!=year || actual.get(Calendar.MONTH)+1!=month)throw new IOException("Gold history month mismatch");
            double p24=MarketData.number(values.get(1)),p22=MarketData.number(values.get(2));
            if(p24<=0 || p22/p24<0.88 || p22/p24>0.97)throw new IOException("Invalid gold history rate");
            if(t<=System.currentTimeMillis()+86400000L){q22.put(t,p22);q24.put(t,p24);count++;}
        }
        if(count==0)throw new IOException("Gold history unavailable for "+year+"-"+month);
    }
    static MarketData.Quote quote(String id,String name,String source,TreeMap<Long,Double> points) throws IOException {
        if(points.size()<2)throw new IOException("Not enough price history");
        MarketData.Quote q=new MarketData.Quote();q.id=id;q.name=name;q.source=source;
        for(Map.Entry<Long,Double> point:points.entrySet()){q.times.add(point.getKey());q.values.add(point.getValue());}
        int n=q.values.size();q.price=q.values.get(n-1);q.previous=q.values.get(n-2);q.asOf=points.lastKey();return q;
    }
    static MarketData.Quote slice(MarketData.Quote source,String range) {
        if(source==null)return null;
        Calendar from=Calendar.getInstance(MarketData.IST);from.setTimeInMillis(source.asOf);
        from.set(Calendar.HOUR_OF_DAY,0);from.set(Calendar.MINUTE,0);from.set(Calendar.SECOND,0);from.set(Calendar.MILLISECOND,0);
        if(range.equals("1y"))from.add(Calendar.YEAR,-1);
        else if(range.equals("1mo"))from.add(Calendar.MONTH,-1);
        else if(range.equals("1wk"))from.add(Calendar.DAY_OF_MONTH,-6);
        MarketData.Quote q=new MarketData.Quote();q.id=source.id;q.name=source.name;q.source=source.source;
        q.price=source.price;q.previous=source.previous;q.asOf=source.asOf;q.currency=source.currency;q.cached=source.cached;q.error=source.error;
        for(int i=0;i<source.times.size();i++)if(source.times.get(i)>=from.getTimeInMillis()){
            q.times.add(source.times.get(i));q.values.add(source.values.get(i));
        }
        return q;
    }
}
