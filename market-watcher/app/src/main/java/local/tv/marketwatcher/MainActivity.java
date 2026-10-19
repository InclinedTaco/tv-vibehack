package local.tv.marketwatcher;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final int BG=0xff090d13, PANEL=0xff121a24, TEXT=0xfff2f5fa, MUTED=0xff8d9cae, GOLD=0xffe7c785, SILVER=0xffc8d9ec, GREEN=0xff77deb2, RED=0xffff8c92;
    final Handler handler=new Handler();
    final ExecutorService pool=Executors.newFixedThreadPool(3);
    final Map<String,MarketData.Quote> quotes=new HashMap<>();
    final Map<String,String> errors=new HashMap<>();
    final Set<String> pending=new HashSet<>();
    final Map<String,Card> cards=new HashMap<>();
    final List<Button> rangeButtons=new ArrayList<>();
    SharedPreferences prefs;
    TextView refreshStatus, detailTitle, detailPrice, detailChange, detailStamp, footer;
    ChartView chart;
    LinearLayout watchGrid;
    Button indiaTab, usTab;
    TextView chartPeriod;
    boolean usMarket;
    static final String[] RANGES={"1d","1wk","1mo","1y"};
    String selected="gold22", range="1wk";
    long lastRefresh, lastGold, lastSilver;
    boolean active, destroyed;
    String lastDetailKey;
    MarketData.Quote lastDetailQuote,lastDailyQuote;
    final Runnable renderPending=()->render();
    void queueRender(){handler.removeCallbacks(renderPending);handler.postDelayed(renderPending,60);}
    void assign(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
    final Runnable periodic=()->{if(active){refresh(false);schedule();}};
    int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create(size>=27?"sans-serif-light":"sans-serif",Typeface.NORMAL));t.setFontFeatureSettings("tnum");return t;}
    GradientDrawable background(int color, int stroke){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(14));if(stroke!=0)g.setStroke(dp(1.5f),stroke);return g;}
    LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(TEXT);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(12),0,dp(12),0);b.setBackground(background(PANEL,0));b.setOnFocusChangeListener((v,f)->{b.setBackground(background(f?0xff263a4b:PANEL,f?0xffcbd7e5:0));b.animate().scaleX(f?1.04f:1).scaleY(f?1.04f:1).setDuration(160).setInterpolator(new DecelerateInterpolator()).withLayer().start();});return b;}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);fullscreen();
        prefs=getSharedPreferences("market",MODE_PRIVATE);
        LinearLayout root=col();root.setPadding(dp(10),dp(6),dp(10),dp(6));root.setBackgroundColor(BG);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("Market Watcher",27,TEXT);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));header.addView(title,new LinearLayout.LayoutParams(0,dp(43),1));
        refreshStatus=text("Connecting…",12,MUTED);refreshStatus.setGravity(Gravity.CENTER_VERTICAL);header.addView(refreshStatus,lp(185,36));
        Button refresh=button("↻  Refresh");refresh.setOnClickListener(v->refresh(true));header.addView(refresh,lp(106,36));root.addView(header,lp(-1,48));

        LinearLayout main=new LinearLayout(this);LinearLayout.LayoutParams mainP=new LinearLayout.LayoutParams(-1,0,1);mainP.setMargins(0,dp(8),0,dp(14));root.addView(main,mainP);
        LinearLayout gold=col();TextView gl=text("CHENNAI METALS  /  ₹ PER GRAM",12,GOLD);gl.setPadding(dp(2),0,0,dp(9));gold.addView(gl);
        Card g22=new Card("gold22","22K · Jewellery gold",true),g24=new Card("gold24","24K · Pure gold",true),silver=new Card("silver","Silver · Per gram",true);cards.put("gold22",g22);cards.put("gold24",g24);cards.put("silver",silver);
        for(Card metal:new Card[]{g22,g24,silver}){LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,0,1);gp.setMargins(0,0,0,dp(7));gold.addView(metal,gp);}
        TextView gn=text("Published local rates · final shop charges vary",10,MUTED);gn.setPadding(0,dp(7),0,0);gold.addView(gn);main.addView(gold,lp(265,-1));

        LinearLayout detail=col();detail.setPadding(dp(20),dp(15),dp(20),dp(12));detail.setBackground(background(PANEL,0));
        LinearLayout.LayoutParams detailP=new LinearLayout.LayoutParams(0,-1,1);detailP.setMargins(dp(18),0,0,0);main.addView(detail,detailP);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(0,dp(3),dp(4),dp(3));heading.setClipChildren(false);heading.setClipToPadding(false);detail.setClipChildren(false);detail.setClipToPadding(false);
        detailTitle=text("Chennai · 22K gold",19,TEXT);heading.addView(detailTitle,new LinearLayout.LayoutParams(0,dp(31),1));
        for(String r:RANGES){Button b=button(r.equals("1mo")?"1M":r.equals("1wk")?"1W":r.toUpperCase(Locale.ROOT));b.setId(View.generateViewId());rangeButtons.add(b);LinearLayout.LayoutParams rp=lp(48,34);rp.setMargins(dp(5),0,0,0);heading.addView(b,rp);b.setOnClickListener(v->{range=r;requestRange();render();});b.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_DOWN||(key==KeyEvent.KEYCODE_DPAD_LEFT&&b==rangeButtons.get(0))){return focusSelectedCard();}return false;});}
        chartPeriod=text("10 DAYS",11,GOLD);chartPeriod.setGravity(Gravity.CENTER);heading.addView(chartPeriod,lp(90,30));
        detail.addView(heading);
        LinearLayout prices=new LinearLayout(this);prices.setGravity(Gravity.CENTER_VERTICAL);
        detailPrice=text("—",34,TEXT);prices.addView(detailPrice);
        detailChange=text("",13,GREEN);detailChange.setPadding(dp(16),0,0,0);prices.addView(detailChange);detail.addView(prices,lp(-1,47));
        detailStamp=text("Fetching latest published rates…",11,MUTED);detail.addView(detailStamp);
        chart=new ChartView();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,0,1);cp.topMargin=dp(12);detail.addView(chart,cp);

        LinearLayout watchHeader=new LinearLayout(this);watchHeader.setGravity(Gravity.CENTER_VERTICAL);TextView watch=text("WATCHLIST",11,MUTED);watchHeader.addView(watch,new LinearLayout.LayoutParams(0,dp(35),1));
        indiaTab=button("India · INR");usTab=button("US · USD");watchHeader.addView(indiaTab,lp(110,29));LinearLayout.LayoutParams ut=lp(110,29);ut.leftMargin=dp(7);watchHeader.addView(usTab,ut);root.addView(watchHeader);
        indiaTab.setOnClickListener(v->switchMarket(false));usTab.setOnClickListener(v->switchMarket(true));
        watchGrid=col();watchGrid.setClipChildren(false);root.addView(watchGrid);buildWatchlist();
        footer=text("OK: chart · Back: cards  |  Gold: LiveChennai · Silver: Goodreturns · daily rates  |  Stocks: Yahoo · may be delayed",10,MUTED);footer.setPadding(0,dp(1),0,0);root.addView(footer);
        // Focus scaling extends into the outer padding; do not crop its rounded outline.
        root.setClipChildren(false);root.setClipToPadding(false);main.setClipChildren(false);gold.setClipChildren(false);setContentView(root);loadCache();render();g22.requestFocus();
    }
    void buildWatchlist(){watchGrid.removeAllViews();cards.entrySet().removeIf(e->!MetalHistory.isMetal(e.getKey()));String[][] stocks=usMarket?MarketData.US:MarketData.INDIA;for(int row=0;row<2;row++){LinearLayout strip=new LinearLayout(this);strip.setClipChildren(false);for(int c=0;c<4;c++){String[] stock=stocks[row*4+c];Card card=new Card(stock[0],stock[1],false);cards.put(stock[0],card);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(63),1);p.setMargins(0,0,c==3?0:dp(10),dp(8));strip.addView(card,p);}watchGrid.addView(strip);}}
    void switchMarket(boolean us){if(usMarket==us)return;usMarket=us;buildWatchlist();selected=us?"AAPL":"^NSEI";range="1d";refresh(true);render();}
    void select(String id){if(selected.equals(id))return;selected=id;range=MetalHistory.isMetal(id)?"1wk":"1d";if(!MetalHistory.isMetal(id))requestStock(id,"1d",false);render();}
    final class Card extends LinearLayout {
        final String id;final TextView value,change;final boolean gold;MiniChart trend;int lastStyle=-1;MarketData.Quote lastQuote;
        Card(String id,String title,boolean gold){super(MainActivity.this);this.id=id;this.gold=gold;setOrientation(LinearLayout.VERTICAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(14),dp(7),dp(12),dp(7));setFocusable(true);setClickable(true);
            TextView name=text(title,gold?14:12,id.equals("silver")?SILVER:gold?GOLD:MUTED);if(gold)addView(name);else{LinearLayout heading=new LinearLayout(MainActivity.this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));trend=new MiniChart();heading.addView(trend,lp(51,14));addView(heading);}
            LinearLayout line=new LinearLayout(MainActivity.this);line.setGravity(Gravity.CENTER_VERTICAL);value=text("—",gold?26:20,TEXT);line.addView(value,new LinearLayout.LayoutParams(0,-2,1));
            change=text("",gold?12:11,GREEN);line.addView(change);addView(line);
            Runnable preview=()->{if(isFocused())select(id);};setContentDescription(title);setOnFocusChangeListener((v,f)->{style();animate().scaleX(f?1.018f:1).scaleY(f?1.025f:1).setDuration(180).setInterpolator(new DecelerateInterpolator()).withLayer().start();handler.removeCallbacks(preview);if(f)handler.postDelayed(preview,120);});setOnClickListener(v->{select(id);focusRanges();});setOnKeyListener((v,key,event)->{if(event.getAction()==KeyEvent.ACTION_DOWN&&gold&&key==KeyEvent.KEYCODE_DPAD_RIGHT){select(id);focusRanges();return true;}return false;});
        }
        void style(){int state=isFocused()?2:id.equals(selected)?1:0;if(state==lastStyle)return;lastStyle=state;setBackground(background(isFocused()?0xff243442:PANEL,isFocused()?(id.equals("silver")?SILVER:gold?GOLD:GREEN):id.equals(selected)?0xff486076:0));}
        void update(MarketData.Quote q){style();if(q==null){assign(value,errors.containsKey(id)?"Unavailable":"—");assign(change,"");return;}if(q==lastQuote)return;lastQuote=q;if(trend!=null){trend.quote=q;trend.invalidate();}assign(value,price(q));assign(change,delta(q,false));change.setTextColor(q.price>=q.previous?GREEN:RED);setContentDescription(q.name+" "+price(q)+", "+delta(q,true)+", as of "+MarketData.format(q.asOf,"dd MMM HH:mm"));}
    }
    String money(double n){NumberFormat f=NumberFormat.getNumberInstance(new Locale("en","IN"));f.setMaximumFractionDigits(2);f.setMinimumFractionDigits(n==Math.rint(n)?0:2);return "₹"+f.format(n);}
    String price(MarketData.Quote q){String n=money(q.price).substring(1);return q.id.startsWith("^")?n:("USD".equals(q.currency)?"$":"₹")+n;}
    String delta(MarketData.Quote q,boolean full){if(!Double.isFinite(q.previous)||q.previous<=0)return "";double d=q.price-q.previous,p=d/q.previous*100;String sign=d>=0?"+":"−";String amount=money(Math.abs(d));if(q.id.startsWith("^"))amount=amount.substring(1)+" pts";else if("USD".equals(q.currency))amount="$"+amount.substring(1);return (full?sign+amount+"  ":"")+String.format(Locale.ENGLISH,"%s%.2f%%",sign,Math.abs(p));}
    String key(String id,String r){return id+":"+r;}
    void chooseQuote(MarketData.Quote q,String k){quotes.put(k,q);errors.remove(q.id);}
    void loadCache(){
        try {String raw=prefs.getString("gold",null);if(raw!=null)for(MarketData.Quote q:MarketData.gold(raw)){q.cached=true;quotes.put(q.id,q);}}catch(Exception ignored){}
        try {String raw=prefs.getString("silver",null);if(raw!=null){MarketData.Quote q=MetalHistory.silver(raw);q.cached=true;quotes.put(q.id,q);}}catch(Exception ignored){}
        for(String[] s:MarketData.allStocks())for(String r:RANGES)try{String raw=prefs.getString(key(s[0],r),null);if(raw!=null){MarketData.Quote q=MarketData.stock(raw,s[0],s[1]);q.cached=true;quotes.put(key(s[0],r),q);}}catch(Exception ignored){}
    }
    void render(){
        if(destroyed)return;
        for(Map.Entry<String,Card> e:cards.entrySet())e.getValue().update(quotes.get(MetalHistory.isMetal(e.getKey())?e.getKey():key(e.getKey(),"1d")));
        boolean gold=MetalHistory.isMetal(selected);
        String historyKey="goldHistory:"+range;
        MarketData.Quote q=quotes.get(gold?(selected.startsWith("gold")&&(range.equals("1mo")||range.equals("1y"))?key(selected,range):selected):key(selected,range));
        MarketData.Quote daily=quotes.get(gold?selected:key(selected,"1d"));
        assign(refreshStatus,pending.isEmpty()?"Auto-refresh · 60 sec":"Refreshing · "+pending.size()+" feeds");
        String detailKey=selected+":"+range+":"+(q!=null&&q.cached)+":"+errors.get(selected)+":"+errors.get(historyKey)+":"+pending.contains(historyKey);
        if(Objects.equals(lastDetailKey,detailKey)&&lastDetailQuote==q&&lastDailyQuote==daily)return;
        lastDetailKey=detailKey;lastDetailQuote=q;lastDailyQuote=daily;
        if(gold)q=MetalHistory.slice(q,range);
        for(int i=0;i<rangeButtons.size();i++){Button b=rangeButtons.get(i);b.setVisibility(View.VISIBLE);b.setText(new String[]{"1D","1W","1M","1Y"}[i]);b.setTextColor(range.equals(RANGES[i])?TEXT:MUTED);if(!b.isFocused())b.setBackground(background(range.equals(RANGES[i])?0xff304253:PANEL,0));}
        chartPeriod.setVisibility(View.GONE);indiaTab.setTextColor(usMarket?MUTED:TEXT);usTab.setTextColor(usMarket?TEXT:MUTED);
        String title=selected.equals("silver")?"Chennai · Silver":gold?(selected.equals("gold22")?"Chennai · 22K gold":"Chennai · 24K gold"):stockName(selected);
        MarketData.Quote headline=gold&&daily!=null?daily:q;
        detailTitle.setText(title);detailPrice.setText(headline==null?"—":price(headline)+(gold?" / g":""));
        detailChange.setText(daily==null?"":selected.equals("silver")?money(daily.price*1000)+" / kg  ·  "+delta(daily,false):delta(daily,true));detailChange.setTextColor(daily==null||daily.price>=daily.previous?GREEN:RED);
        if(q==null){detailStamp.setText(errors.containsKey(selected)||errors.containsKey(historyKey)?"History unavailable · select Refresh to retry":pending.contains(historyKey)?"Loading published gold history…":"Loading market data…");}
        else {
            String period=range.equals("1d")?(gold?"1 daily rate":"Intraday"):range.equals("1wk")?(gold?"1 week":"5 trading sessions"):range.equals("1mo")?"1 month":"1 year";
            detailStamp.setText((q.cached?"Saved data · ":"")+MarketData.format(q.asOf,gold?"dd MMM yyyy":"dd MMM, HH:mm")+(gold?" · daily rates":" IST")+" · "+period+" · "+q.values.size()+" points · "+q.source+(q.error==null?"":" · refresh failed"));
        }
        chart.setData(q,gold,!gold&&range.equals("1d"),selected+":"+range);
        refreshStatus.setText(pending.isEmpty()?"Auto-refresh · 60 sec":"Refreshing · "+pending.size()+" feeds");
    }
    String stockName(String id){for(String[] s:MarketData.allStocks())if(s[0].equals(id))return s[1];return id;}
    void focusRanges(){rangeButtons.get(Arrays.asList(RANGES).indexOf(range)).requestFocus();}
    boolean focusSelectedCard(){Card card=cards.get(selected);return card!=null&&card.requestFocus();}
    @Override public void onBackPressed(){for(Button b:rangeButtons)if(b.hasFocus()){focusSelectedCard();return;}super.onBackPressed();}
    void requestRange(){if(selected.startsWith("gold")){if(range.equals("1mo")||range.equals("1y"))requestGoldHistory(range,false);}else if(selected.equals("silver")){if(!quotes.containsKey("silver"))requestSilver();}else requestStock(selected,range,false);}
    void requestSilver(){
        if(pending.contains("silver"))return;pending.add("silver");
        pool.execute(()->{try{String raw=MetalHistory.silverJson();MarketData.Quote quote=MetalHistory.silver(raw);prefs.edit().putString("silver",raw).apply();
            handler.post(()->{if(destroyed)return;chooseQuote(quote,"silver");lastSilver=System.currentTimeMillis();pending.remove("silver");Log.i("MarketWatcher","Loaded Chennai silver: "+quote.values.size()+" dates");queueRender();});
        }catch(Exception failure){handler.post(()->{if(destroyed)return;pending.remove("silver");errors.put("silver","Silver source unavailable");MarketData.Quote old=quotes.get("silver");if(old!=null){old.cached=true;old.error="refresh failed";}Log.w("MarketWatcher","Silver fetch failed: "+failure.getClass().getSimpleName());render();});}});
    }
    void requestGoldHistory(String requested,boolean force){
        String pendingKey="goldHistory:"+requested;
        if(pending.contains(pendingKey)||(!force&&quotes.containsKey(key("gold22",requested))&&System.currentTimeMillis()-prefs.getLong("historyFresh:"+requested,0)<900000))return;
        pending.add(pendingKey);errors.remove(pendingKey);queueRender();
        pool.execute(()->{try{
            TreeMap<Long,Double> h22=new TreeMap<>(),h24=new TreeMap<>();
            Calendar current=Calendar.getInstance(MarketData.IST),month=(Calendar)current.clone();
            month.set(Calendar.DAY_OF_MONTH,1);month.add(Calendar.MONTH,requested.equals("1y")?-12:-1);
            for(int i=0;i<(requested.equals("1y")?13:2);i++,month.add(Calendar.MONTH,1)){
                if(Thread.currentThread().isInterrupted())return;
                int year=month.get(Calendar.YEAR),number=month.get(Calendar.MONTH)+1;
                String cacheKey="goldMonth:"+year+"-"+number,raw=prefs.getString(cacheKey,null);
                boolean thisMonth=year==current.get(Calendar.YEAR)&&number==current.get(Calendar.MONTH)+1;
                if(raw==null||(thisMonth&&(force||System.currentTimeMillis()-prefs.getLong(cacheKey+":time",0)>900000))){
                    raw=MarketData.get(MetalHistory.goldMonthUrl(year,number));
                    MetalHistory.addGoldMonth(raw,year,number,h22,h24);
                    prefs.edit().putString(cacheKey,raw).putLong(cacheKey+":time",System.currentTimeMillis()).apply();
                }else MetalHistory.addGoldMonth(raw,year,number,h22,h24);
            }
            MarketData.Quote q22=MetalHistory.quote("gold22","Chennai · 22K gold","LiveChennai",h22),q24=MetalHistory.quote("gold24","Chennai · 24K gold","LiveChennai",h24);
            handler.post(()->{if(destroyed)return;chooseQuote(q22,key("gold22",requested));chooseQuote(q24,key("gold24",requested));prefs.edit().putLong("historyFresh:"+requested,System.currentTimeMillis()).apply();pending.remove(pendingKey);errors.remove(pendingKey);Log.i("MarketWatcher","Loaded gold "+requested+": "+q22.values.size()+" dates");queueRender();});
        }catch(Exception failure){handler.post(()->{if(destroyed)return;pending.remove(pendingKey);errors.put(pendingKey,"History unavailable");for(String id:new String[]{"gold22","gold24"}){MarketData.Quote old=quotes.get(key(id,requested));if(old!=null){old.cached=true;old.error="refresh failed";}}Log.w("MarketWatcher","Gold history fetch failed",failure);render();});}});
    }
    void requestStock(String id,String r,boolean force){
        if(MetalHistory.isMetal(id))return;String k=key(id,r);
        if(pending.contains(k)||(!force&&quotes.containsKey(k)&&!quotes.get(k).cached))return;
        pending.add(k);queueRender();
        pool.execute(()->{
            try{String raw=MarketData.get(MarketData.stockUrl(id,r));MarketData.Quote q=MarketData.stock(raw,id,stockName(id));prefs.edit().putString(k,raw).apply();
                handler.post(()->{if(destroyed)return;chooseQuote(q,k);pending.remove(k);Log.i("MarketWatcher","Loaded "+id+" "+r+": "+q.values.size()+" points");queueRender();});
            }catch(Exception e){handler.post(()->{if(destroyed)return;pending.remove(k);errors.put(id,e.toString());MarketData.Quote q=quotes.get(k);if(q!=null){q.cached=true;q.error=e.toString();}Log.w("MarketWatcher","Fetch failed "+id,e);render();});}
        });
    }
    void requestGold(){
        if(pending.contains("gold"))return;pending.add("gold");
        pool.execute(()->{try{String raw=MarketData.get(MarketData.GOLD_URL);MarketData.Quote[] q=MarketData.gold(raw);prefs.edit().putString("gold",raw).apply();
            handler.post(()->{if(destroyed)return;for(MarketData.Quote item:q)chooseQuote(item,item.id);lastGold=System.currentTimeMillis();pending.remove("gold");Log.i("MarketWatcher","Loaded Chennai gold: "+q[0].values.size()+" dates");queueRender();});
        }catch(Exception e){handler.post(()->{if(destroyed)return;pending.remove("gold");for(String id:new String[]{"gold22","gold24"}){errors.put(id,e.toString());MarketData.Quote q=quotes.get(id);if(q!=null){q.cached=true;q.error=e.toString();}}Log.w("MarketWatcher","Gold fetch failed",e);render();});}});
    }
    void refresh(boolean force){long now=System.currentTimeMillis();if(!force&&now-lastRefresh<55000)return;lastRefresh=now;for(String[] s:usMarket?MarketData.US:MarketData.INDIA)requestStock(s[0],"1d",true);if(force||now-lastGold>900000)requestGold();if(force||now-lastSilver>900000)requestSilver();if(selected.startsWith("gold")&&(range.equals("1mo")||range.equals("1y")))requestGoldHistory(range,force);if(!MetalHistory.isMetal(selected)&&!range.equals("1d"))requestStock(selected,range,true);render();}
    void schedule(){handler.removeCallbacks(periodic);handler.postDelayed(periodic,60000);}
    void fullscreen(){
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)fullscreen();}
    @Override protected void onResume(){super.onResume();fullscreen();active=true;refresh(false);schedule();}
    @Override protected void onPause(){active=false;handler.removeCallbacks(periodic);super.onPause();}
    @Override protected void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);pool.shutdownNow();super.onDestroy();}

    final class ChartView extends View {
        MarketData.Quote quote;boolean gold,intraday;final Paint p=new Paint(3);
        String series;
        ChartView(){super(MainActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void setData(MarketData.Quote q,boolean g,boolean intraday,String key){boolean changed=!Objects.equals(series,key)||quote==null&&q!=null;quote=q;gold=g;this.intraday=intraday;series=key;invalidate();if(changed&&q!=null){animate().cancel();setAlpha(0.45f);animate().alpha(1).setDuration(180).withLayer().start();}}
        void label(Canvas c,String s,float x,float y,int color){p.setColor(color);p.setTextSize(10);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setStyle(Paint.Style.FILL);c.drawText(s,x,y,p);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;canvas.save();canvas.scale(density,density);float w=getWidth()/density,h=getHeight()/density;
            if(quote==null||quote.values.isEmpty()){label(canvas,"Waiting for price history…",12,h/2,MUTED);canvas.restore();return;}
            if(quote.values.size()==1){
                p.setColor(quote.id.equals("silver")?SILVER:GOLD);canvas.drawCircle(w/2,h/2-20,5,p);
                label(canvas,"One published daily rate · no intraday metal feed",12,h/2+10,MUTED);
                label(canvas,MarketData.format(quote.times.get(0),"dd MMM yyyy"),12,h-10,MUTED);
                canvas.restore();return;
            }
            List<Double> a=quote.values;List<Long> ts=quote.times;double min=Collections.min(a),max=Collections.max(a);if(max-min<0.01){max+=1;min-=1;}
            double pad=(max-min)*0.12;max+=pad;min-=pad;
            float left=4,right=w-57,top=12,bottom=h-23;if(right<=left||bottom<=top){canvas.restore();return;}
            p.setStrokeWidth(0.6f);
            for(int i=0;i<3;i++){float y=top+(bottom-top)*i/2;p.setColor(0xff2a3441);canvas.drawLine(left,y,right,y,p);label(canvas,String.format(Locale.ENGLISH,"%,.0f",max-(max-min)*i/2),right+7,y+3,MUTED);}
            Path line=new Path();float lastX=0,lastY=0;long start=ts.get(0),end=ts.get(ts.size()-1);
            for(int i=0;i<a.size();i++){float x=left+(right-left)*(end==start?(float)i/(a.size()-1):(float)(ts.get(i)-start)/(end-start));float y=bottom-(float)((a.get(i)-min)/(max-min))*(bottom-top);if(i==0)line.moveTo(x,y);else line.lineTo(x,y);lastX=x;lastY=y;}
            int color=quote.id.equals("silver")?SILVER:gold?GOLD:(intraday?quote.price>=quote.previous:a.get(a.size()-1)>=a.get(0))?GREEN:RED;
            Path area=new Path(line);area.lineTo(lastX,bottom);area.lineTo(left,bottom);area.close();p.setShader(new LinearGradient(0,top,0,bottom,(color&0x00ffffff)|0x33000000,0x00121a24,Shader.TileMode.CLAMP));p.setStyle(Paint.Style.FILL);canvas.drawPath(area,p);p.setShader(null);
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.2f);p.setStrokeJoin(Paint.Join.ROUND);canvas.drawPath(line,p);p.setStyle(Paint.Style.FILL);canvas.drawCircle(lastX,lastY,3.5f,p);
            label(canvas,MarketData.format(start,intraday?"HH:mm":"dd MMM"),left,h-5,MUTED);
            String endText=MarketData.format(end,intraday?"HH:mm":"dd MMM");p.setTextSize(10);label(canvas,endText,right-p.measureText(endText),h-5,MUTED);
            canvas.restore();
        }
    }
    final class MiniChart extends View {
        MarketData.Quote quote;final Paint paint=new Paint(3);
        MiniChart(){super(MainActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){if(quote==null||quote.values.size()<2)return;List<Double> v=quote.values;double lo=Collections.min(v),hi=Collections.max(v);if(hi-lo<0.01){hi+=1;lo-=1;}Path path=new Path();float pad=dp(2),w=getWidth()-2*pad,h=getHeight()-2*pad;for(int i=0;i<v.size();i++){float x=pad+w*i/(v.size()-1),y=pad+h-(float)((v.get(i)-lo)/(hi-lo))*h;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.1f));paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(quote.price>=quote.previous?GREEN:RED);c.drawPath(path,paint);}
    }
}
