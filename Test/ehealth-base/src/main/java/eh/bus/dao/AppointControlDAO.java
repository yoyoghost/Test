package eh.bus.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.bus.AppointControl;
import eh.entity.bus.AppointSource;
import eh.utils.DateConversion;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.Date;

/**
 * @author jianghc
 * @create 2017-03-31 10:14
 **/
public abstract class AppointControlDAO extends HibernateSupportDelegateDAO<AppointControl> {
    public AppointControlDAO() {
        super();
        this.setEntityName(AppointControl.class.getName());
        this.setKeyField("Id");
    }

    @DAOMethod
    public abstract AppointControl getByObjTypeAndObjId(Integer objType, Integer objId);

    public QueryResult<AppointControl> queryAppointControl(final Integer objType, final Integer objId, final int start, final int limit) {
        StringBuffer sb = new StringBuffer(" from AppointControl where 1=1");
        if (objType != null) {
            sb.append(" and objType=:objType");
        }
        if (objId != null) {
            sb.append(" and objId=:objId");
        }
        final String hql = sb.toString();
        HibernateStatelessResultAction<QueryResult<AppointControl>> action =
                new AbstractHibernateStatelessResultAction<QueryResult<AppointControl>>() {
                    @Override
                    public void execute(StatelessSession ss) throws Exception {
                        Query query = ss.createQuery("SELECT COUNT(*) " + hql);
                        if (objType != null) {
                            query.setParameter("objType", objType);
                        }
                        if (objId != null) {
                            query.setParameter("objId", objId);
                        }
                        long total = (long) query.uniqueResult();
                        query = ss.createQuery(hql + " order by id desc");
                        if (objType != null) {
                            query.setParameter("objType", objType);
                        }
                        if (objId != null) {
                            query.setParameter("objId", objId);
                        }
                        setResult(new QueryResult<AppointControl>(total, start, limit, query.list()));
                    }
                };
        HibernateSessionTemplate.instance().executeReadOnly(action);

        return action.getResult();
    }


    public boolean checkAppoint(AppointSource source) {
        if (source == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "source is require");
        }
        Integer organId = source.getOrganId();
        AppointDepartDAO appointDepartDAO = DAOFactory.getDAO(AppointDepartDAO.class);
        Integer deptId = appointDepartDAO.getIdByOrganIdAndAppointDepartCode(organId, source.getAppointDepartCode());
        AppointControl appointControl = this.getByObjTypeAndObjId(2, deptId);
        if (appointControl == null) {
            appointControl = this.getByObjTypeAndObjId(1, organId);
            if (appointControl == null) {
                return true;
            }
        }
        Date now = new Date();//获取当前时间
        Date workDate = source.getWorkDate();
        if (workDate.compareTo(DateConversion.getFormatDate(now,DateConversion.YYYY_MM_DD)) < 0) {
            throw new DAOException("source lose efficacy");
        }
        if (DateConversion.isSameDay(workDate, now)) { // 当天挂号
            if (appointControl.getRegisterTime() == null) {
                return true;
            }
            Date registerTime = DateConversion.getDateByTimePoint(now,
                    DateConversion.getDateFormatter(appointControl.getRegisterTime(), "HH:mm"));
            return now.compareTo(registerTime)>=0?true:false;
        } else { // 提前预约挂号
            Integer startDay = appointControl.getStartDay();
            Integer endDay = appointControl.getEndDay();
            if (startDay != null && startDay > 0) {
                Date startDate = DateConversion.getDateByTimePoint(DateConversion.getDateAftXDays(workDate, -startDay),
                        DateConversion.getDateFormatter(appointControl.getStartTime(), "HH:mm"));
                if(startDate.compareTo(now)>0){
                   return false;
                }
            }
            if (endDay != null && endDay > 0) {
                Date endDate = DateConversion.getDateByTimePoint(DateConversion.getDateAftXDays(workDate, -endDay),
                        DateConversion.getDateFormatter(appointControl.getEndTime(), "HH:mm"));
                if(endDate.compareTo(now)<=0){
                    return false;
                }
            }
            return true;
        }
    }






}