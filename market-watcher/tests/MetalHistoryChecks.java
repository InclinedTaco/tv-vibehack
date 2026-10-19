package local.tv.marketwatcher;
import java.nio.file.*;
import java.util.*;

/** Desktop checks using org.json; no Android device or network required. */
public final class MetalHistoryChecks {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    interface Task{void run()throws Exception;}
    static void rejects(Task task)throws Exception{try{task.run();}catch(Exception expected){return;}throw new AssertionError("Invalid source data accepted");}
    public static void main(String[] args)throws Exception{
        String silver="[{\"on_date\":\"2026-09-01\",\"y\":250000,\"silver_price_1kg\":\"2,50,000\"},{\"on_date\":\"2026-09-02\",\"y\":245000,\"silver_price_1kg\":\"2,45,000\"}]";
        MarketData.Quote q=MetalHistory.silver(silver);
        check(q.price==245&&q.previous==250,"Silver kg-to-gram conversion");
        rejects(()->MetalHistory.silver(silver.replace("245000","244000")));
        rejects(()->MetalHistory.silver(silver.replace("2026-09-02","2026-02-30")));
        String gold="<table><tr><td>01/September/2026</td><td>15464.00</td><td>14175.00</td></tr><tr><td>02/September/2026</td><td>15218.00</td><td>13950.00</td></tr></table>";
        TreeMap<Long,Double> g22=new TreeMap<>(),g24=new TreeMap<>();
        MetalHistory.addGoldMonth(gold,2026,9,g22,g24);
        check(g22.size()==2&&g22.lastEntry().getValue()==13950,"Gold monthly parsing");
        rejects(()->MetalHistory.addGoldMonth(gold,2026,8,new TreeMap<>(),new TreeMap<>()));
        rejects(()->MetalHistory.addGoldMonth(gold.replace("13950.00","100.00"),2026,9,new TreeMap<>(),new TreeMap<>()));
        TreeMap<Long,Double> history=new TreeMap<>();Calendar day=Calendar.getInstance(MarketData.IST);day.clear();day.set(2025,Calendar.SEPTEMBER,1);
        for(int i=0;i<400;i++,day.add(Calendar.DAY_OF_MONTH,1))history.put(day.getTimeInMillis(),200.0+i);
        MarketData.Quote full=MetalHistory.quote("silver","Silver","Fixture",history);
        check(MetalHistory.slice(full,"1d").values.size()==1,"Daily slice");
        check(MetalHistory.slice(full,"1wk").values.size()==7,"Weekly slice");
        int month=MetalHistory.slice(full,"1mo").values.size(),year=MetalHistory.slice(full,"1y").values.size();
        check(month>=29&&month<=32,"Calendar month slice");check(year>=365&&year<=367,"Calendar year slice");
        if(args.length==2){
            MarketData.Quote live=MetalHistory.silver(Files.readString(Path.of(args[0])));
            check(live.values.size()>300,"Silver yearly coverage");
            TreeMap<Long,Double>a=new TreeMap<>(),b=new TreeMap<>();MetalHistory.addGoldMonth(Files.readString(Path.of(args[1])),2026,9,a,b);
            check(a.size()==30,"September archive coverage");
            System.out.println("Source snapshots: "+live.values.size()+" silver dates, "+a.size()+" gold dates.");
        }
        System.out.println("PASS: units, invalid-data rejection, archive dates, week/month/year filters.");
    }
}
