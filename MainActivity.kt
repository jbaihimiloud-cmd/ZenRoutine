package com.example.zenroutine

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

object Routine {
 val titles = listOf("التأمل والتنفس · 10 دقائق", "الحركة والمشي · 15 دقيقة", "وجبة متوازنة", "القراءة والتأمل · 20 دقيقة", "الهدوء قبل النوم · 10 دقائق")
 val defaults = listOf("06:00", "06:15", "12:00", "20:00", "22:00")
 fun time(ctx: Context, i:Int):String = ctx.getSharedPreferences("routine",0).getString("time$i",defaults[i])!!
 fun enabled(ctx:Context,i:Int):Boolean = ctx.getSharedPreferences("routine",0).getBoolean("enabled$i",true)
 fun schedule(ctx:Context,i:Int) {
  val am=ctx.getSystemService(AlarmManager::class.java)
  val intent=Intent(ctx,AlarmReceiver::class.java).putExtra("index",i)
  val pi=PendingIntent.getBroadcast(ctx,100+i,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  am.cancel(pi)
  if(!enabled(ctx,i)) return
  val parts=time(ctx,i).split(":").map { it.toInt() }
  val cal=Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,parts[0]);set(Calendar.MINUTE,parts[1]);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0);if(timeInMillis<=System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR,1) }
  if(Build.VERSION.SDK_INT>=31 && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.timeInMillis,pi)
  else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.timeInMillis,pi)
 }
 fun scheduleAll(ctx:Context) { titles.indices.forEach { schedule(ctx,it) } }
 fun today():String=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
}

class MainActivity:Activity() {
 private lateinit var list:LinearLayout
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState)
  window.statusBarColor=android.graphics.Color.rgb(14,95,84)
  val root=ScrollView(this); list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(28,36,28,28);layoutDirection=android.view.View.LAYOUT_DIRECTION_RTL }
  root.addView(list);setContentView(root)
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),1)
  if(Build.VERSION.SDK_INT>=31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
   AlertDialog.Builder(this).setTitle("التنبيهات الدقيقة").setMessage("للحصول على تنبيهات دقيقة، فعّل إذن المنبهات والتذكيرات من إعدادات الهاتف.").setPositiveButton("فتح الإعدادات") { _,_-> startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }.setNegativeButton("لاحقاً",null).show()
  }
  Routine.scheduleAll(this);draw()
 }
 override fun onResume(){super.onResume();if(::list.isInitialized){Routine.scheduleAll(this);draw()}}
 private fun draw(){list.removeAllViews()
  label("روتيني الهادئ",26,true);label("برنامج يومي بسيط • تنبيهات محلية بلا إنترنت",15,false)
  val prefs=getSharedPreferences("routine",0)
  val day=Routine.today()
  val completed=Routine.titles.indices.count { prefs.getBoolean("done_${day}_$it",false) }
  label("إنجاز اليوم: $completed / ${Routine.titles.size}",18,true)
  Routine.titles.forEachIndexed { i,title ->
   val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(20,20,20,20);setBackgroundColor(android.graphics.Color.rgb(237,247,243)) }
   val heading=TextView(this).apply { text=title;textSize=19f;setTextColor(android.graphics.Color.rgb(18,72,65)) }
   card.addView(heading)
   val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
   val check=CheckBox(this).apply { text="تم";isChecked=prefs.getBoolean("done_${day}_$i",false);setOnCheckedChangeListener { _,v ->prefs.edit().putBoolean("done_${day}_$i",v).apply();draw() } }
   row.addView(check)
   val timeButton=Button(this).apply { text="الوقت: ${Routine.time(this@MainActivity,i)}";setOnClickListener { editTime(i) } }
   row.addView(timeButton)
   card.addView(row)
   val toggle=Switch(this).apply { text="تفعيل التنبيه";isChecked=Routine.enabled(this@MainActivity,i);setOnCheckedChangeListener { _,v ->prefs.edit().putBoolean("enabled$i",v).apply();Routine.schedule(this@MainActivity,i) } }
   card.addView(toggle)
   list.addView(card,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin=16 })
  }
  label("ملاحظة: قد يؤخر نظام Android بعض التنبيهات بسبب توفير البطارية. افتح التطبيق بعد إعادة تثبيته أو تغيير إعدادات التنبيه.",13,false)
 }
 private fun label(t:String,s:Int,b:Boolean){list.addView(TextView(this).apply {text=t;textSize=s.toFloat();setPadding(0,8,0,16);if(b)setTypeface(null,1)})}
 private fun editTime(i:Int){val p=Routine.time(this,i).split(":");TimePickerDialog(this,{_,h,m->getSharedPreferences("routine",0).edit().putString("time$i",String.format(Locale.US,"%02d:%02d",h,m)).apply();Routine.schedule(this,i);draw()},p[0].toInt(),p[1].toInt(),true).show()}
}

class AlarmReceiver:android.content.BroadcastReceiver(){
 override fun onReceive(ctx:Context,intent:Intent){val i=intent.getIntExtra("index",-1);if(i !in Routine.titles.indices)return
  val nm=ctx.getSystemService(NotificationManager::class.java)
  val channel="daily_routine";nm.createNotificationChannel(NotificationChannel(channel,"تنبيهات الروتين",NotificationManager.IMPORTANCE_HIGH))
  val open=PendingIntent.getActivity(ctx,i,Intent(ctx,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=Notification.Builder(ctx,channel).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("حان وقت النشاط").setContentText(Routine.titles[i]).setAutoCancel(true).setContentIntent(open).build()
  if(Build.VERSION.SDK_INT<33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)nm.notify(100+i,notification)
  Routine.schedule(ctx,i)
 }
}
class BootReceiver:android.content.BroadcastReceiver(){override fun onReceive(ctx:Context,intent:Intent){Routine.scheduleAll(ctx)}}
