package local.tv.marketwatcher;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.*;
import java.util.*;
import java.util.regex.*;

final class MarketData {
    static final TimeZone IST = TimeZone.getTimeZone("Asia/Kolkata");
    static final String GOLD_URL = "https://www.livechennai.com/gold_silverrate.asp";
    static final String[][] INDIA = {
        {"^NSEI","Nifty 50"}, {"^BSESN","Sensex"}, {"RELIANCE.NS","Reliance"}, {"TCS.NS","TCS"},
        {"HDFCBANK.NS","HDFC Bank"}, {"ICICIBANK.NS","ICICI Bank"}, {"INFY.NS","Infosys"}, {"BHARTIARTL.NS","Bharti Airtel"}
    };
    static final String[][] US = {
        {"^GSPC","S&P 500"}, {"^IXIC","Nasdaq"}, {"AAPL","Apple"}, {"MSFT","Microsoft"},
        {"NVDA","Nvidia"}, {"AMZN","Amazon"}, {"GOOGL","Alphabet"}, {"TSLA","Tesla"}
    };
    static String[][] allStocks(){String[][] all=new String[INDIA.length+US.length][];System.arraycopy(INDIA,0,all,0,INDIA.length);System.arraycopy(US,0,all,INDIA.length,US.length);return all;}
    static boolean isUS(String id){for(String[] s:US)if(s[0].equals(id))return true;return false;}
    static class Quote {
        String id, name, source, error, currency="INR";
        double price, previous;
        long asOf;
        boolean cached;
        final List<Double> values = new ArrayList<>();
        final List<Long> times = new ArrayList<>();
    }
    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(9000); c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Android TV; MarketWatcher/1.0)");
        c.setRequestProperty("Accept","application/json,text/html");
        try {
            if(c.getResponseCode()!=200) throw new IOException("Source returned HTTP "+c.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){
                byte[] b=new byte[8192]; int n;
                while((n=in.read(b))!=-1){out.write(b,0,n); if(out.size()>2500000)throw new IOException("Response too large");}
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally { c.disconnect(); }
    }
    static String stockUrl(String id,String range) throws Exception {
        return "https://query1.finance.yahoo.com/v8/finance/chart/"+URLEncoder.encode(id,"UTF-8")
                +"?range="+(range.equals("1wk")?"5d":range)+"&interval="+(range.equals("1d")?"5m":range.equals("1wk")?"30m":range.equals("1y")?"1wk":"1d");
    }
    static Quote stock(String json,String id,String name) throws Exception {
        JSONObject chart = new JSONObject(json).getJSONObject("chart");
        if(!chart.isNull("error")) throw new IOException("Quote unavailable");
        JSONObject r=chart.getJSONArray("result").getJSONObject(0), m=r.getJSONObject("meta");
        String currency=isUS(id)?"USD":"INR";
        if(!id.equals(m.getString("symbol")) || !currency.equals(m.getString("currency")))throw new IOException("Unexpected quote");
        Quote q=new Quote();q.id=id;q.name=name;q.source="Yahoo Finance";
        q.currency=currency;
        q.price=m.getDouble("regularMarketPrice");q.previous=m.optDouble("previousClose",m.optDouble("chartPreviousClose",Double.NaN));q.asOf=m.getLong("regularMarketTime")*1000;
        if(!Double.isFinite(q.price)||q.price<=0||q.asOf<=0)throw new IOException("Invalid quote");
        JSONArray ts=r.optJSONArray("timestamp");
        if(ts!=null){
            JSONArray close=r.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close");
            for(int i=0;i<Math.min(ts.length(),close.length());i++){
                double v=close.optDouble(i,Double.NaN);
                if(Double.isFinite(v)&&v>0){q.times.add(ts.getLong(i)*1000);q.values.add(v);}
            }
        }
        return q;
    }
    static Quote[] gold(String html) throws Exception {
        Quote q22=new Quote(),q24=new Quote();
        q22.id="gold22";q22.name="Chennai · 22K gold";q24.id="gold24";q24.name="Chennai · 24K gold";
        q22.source=q24.source="LiveChennai";
        Matcher rows=Pattern.compile("<tr\\b[^>]*>(.*?)</tr>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(html);
        TreeMap<Long,double[]> history=new TreeMap<>();
        SimpleDateFormat day=new SimpleDateFormat("dd/MMM/yyyy",Locale.ENGLISH);day.setTimeZone(IST);day.setLenient(false);
        while(rows.find()){
            Matcher cells=Pattern.compile("<td\\b[^>]*>(.*?)</td>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(rows.group(1));
            List<String> v=new ArrayList<>();while(cells.find())v.add(clean(cells.group(1)));
            if(v.size()!=5 || !v.get(0).matches("\\d{1,2}/[A-Za-z]{3}/\\d{4}"))continue;
            long t=day.parse(v.get(0)).getTime();
            double p24=number(v.get(1)),eight24=number(v.get(2)),p22=number(v.get(3)),eight22=number(v.get(4));
            if(p24<=0||p22/p24<0.88||p22/p24>0.97||Math.abs(eight24-p24*8)>8||Math.abs(eight22-p22*8)>8)throw new IOException("Gold table format changed");
            history.put(t,new double[]{p22,p24});
        }
        if(history.size()<2)throw new IOException("Gold history unavailable");
        for(Map.Entry<Long,double[]> e:history.entrySet()){q22.times.add(e.getKey());q24.times.add(e.getKey());q22.values.add(e.getValue()[0]);q24.values.add(e.getValue()[1]);}
        int n=q22.values.size();q22.price=q22.values.get(n-1);q24.price=q24.values.get(n-1);q22.previous=q22.values.get(n-2);q24.previous=q24.values.get(n-2);
        long update=history.lastKey();
        Matcher stamp=Pattern.compile("Last Update Time:\\s*([^<]+)",Pattern.CASE_INSENSITIVE).matcher(html);
        if(stamp.find()){
            SimpleDateFormat fmt=new SimpleDateFormat("dd/MM/yyyy h:mm:ss a",Locale.ENGLISH);fmt.setTimeZone(IST);fmt.setLenient(false);
            update=fmt.parse(clean(stamp.group(1))).getTime();
            if(!format(update,"dd MMM yyyy").equals(format(history.lastKey(),"dd MMM yyyy")))throw new IOException("Gold date mismatch");
        }
        q22.asOf=q24.asOf=update;return new Quote[]{q22,q24};
    }
    static String clean(String s){return s.replaceAll("<[^>]+>"," ").replace("&nbsp;"," ").replace("&#8377;","").replaceAll("\\s+"," ").trim();}
    static double number(String s) throws Exception {String value=s.replace(",","").replace("₹","").trim();if(!value.matches("\\d+(\\.\\d+)?"))throw new IOException("Invalid rate");return Double.parseDouble(value);}
    static String format(long t,String pattern){SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.ENGLISH);f.setTimeZone(IST);return f.format(new Date(t));}
}
