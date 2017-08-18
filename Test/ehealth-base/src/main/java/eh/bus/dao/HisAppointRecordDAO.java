package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.util.annotation.RpcService;
import eh.entity.bus.HisAppointRecord;
import org.hibernate.StatelessSession;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public abstract class HisAppointRecordDAO extends HibernateSupportDelegateDAO<HisAppointRecord>{
  public HisAppointRecordDAO(){
	  super();
	  this.setEntityName(HisAppointRecord.class.getName());
	  this.setKeyField("appointRecordId");
  }
  @RpcService
  @DAOMethod(sql="from HisAppointRecord where organId=:organId and organSchedulingId=:organSchedulingId and organSourceId=:organSourceId and workDate=:workDate")
  public abstract HisAppointRecord getHisAppoitRecord(@DAOParam("organId")int organId,@DAOParam("organSchedulingId")String organSchedulingId,@DAOParam("organSourceId")String organSourceId,@DAOParam("workDate")Date workDate);
  @RpcService
  public Boolean saveOrUpdate(final HisAppointRecord source){
	  AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>(){

			@Override
			public void execute(StatelessSession arg0) throws Exception {
				source.setStartTime(source.getWorkDate());
				int organId=source.getOrganId();
				String organSchedulingId=source.getOrganSchedulingId();
				String organSourceId=getOrganSourceID(source);//重新计算号源id
				source.setOrganSourceId(organSourceId);
				source.setAppointDate(new Date());
				source.setStartTime(source.getWorkDate());//预约日期 时间
				source.setEndTime(source.getWorkDate());
				if(source.getType().equals("1")){//预约取消操作
					source.setCancelDate(new Date());
				}
				save(source);
				
				
				
				
/*				HisAppointRecord old=getHisAppoitRecord(organId, organSchedulingId, organSourceId,source.getWorkDate());
				if(source.getType().equals("2")){//预约操作
					if(old==null){
						source.setAppointDate(new Date());
						save(source);
					}else{//更新已用数量
						if(old.getNumber()<1){
							old.setNumber(old.getNumber()+source.getNumber());
							update(old);
						}
					}
						
				}
				if(source.getType().equals("1")){//预约取消操作
					if(old==null){//之前没有保存预约记录的，直接返回 不处理|预约马上取消的有可能不推送预约记录过来
						source.setAppointDate(new Date());
						save(source);
						setResult(true);
						return;
					}else{//已用数减去number
						if(old.getNumber()>=source.getNumber()){
							old.setCancelDate(new Date());
							//old.setType("1");
							old.setNumber(old.getNumber()-source.getNumber());
						    //update(old);
						    //setResult(true);
						}else{
							old.setNumber(0);
						}
						old.setType("1");
					    update(old);
					    setResult(true);
					}
				}*/
			}
			
		};
		HibernateSessionTemplate.instance().execute(action);
		return action.getResult();
  }

	private String getOrganSourceID(HisAppointRecord source) {
		int worktype = 0;
		if (source.getWorkType() != null) {
			worktype = source.getWorkType();
		} else {
			Calendar c = Calendar.getInstance();
			// c.setTime(source.getWorkDate());
			Date start = source.getStartTime();
			c.setTime(start);

			if (c.get(Calendar.HOUR_OF_DAY) < 12) {
				worktype = 1;
				// source.setWorkType(1);
			} else {
				worktype = 2;
				// source.setWorkType(2);
			}
		}

		String dateStr = getDate(source.getWorkDate(), "yyyyMMdd");
		String organSourceID = dateStr + "|" + source.getOrganSchedulingId() + "|" + worktype + "|" + source.getOrderNum();
		return organSourceID;
	}
  /**
	 * 获取系统当期年月日(精确到天)，格式：yyyyMMdd
	 * @return
	 */
	public static String getDate(Date date,String format){
		DateFormat df=new SimpleDateFormat(format);
		
		return df.format(date);
	}
	
}
