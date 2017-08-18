package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.bus.dao.MeetClinicDAO;
import eh.bus.dao.MeetClinicResultDAO;
import eh.bus.dao.QueryMeetClinicHisDAO;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicAndResult;
import eh.entity.bus.MeetClinicResult;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.op.auth.service.SecurityService;
import eh.utils.DateConversion;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Created by andywang on 2016/11/30.
 */
public class MeetClinicOpService {

    @RpcService
    public HashMap<String, Integer> getStatisticsByStatus(final Date startTime, final Date endTime, final MeetClinic mc,
                                                          final MeetClinicResult mr, final int start, final String mpiid,
                                                          final List<Integer> requestOrgans, final List<Integer> targetOrgans) {
        QueryMeetClinicHisDAO queryMeetClinicHisDAO = DAOFactory.getDAO(QueryMeetClinicHisDAO.class);
        return queryMeetClinicHisDAO.getStatisticsByStatus(startTime, endTime, mc, mr, start, mpiid, requestOrgans, targetOrgans);

    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByExeStatus(final Date startTime, final Date endTime, final MeetClinic mc,
                                                             final MeetClinicResult mr, final int start, final String mpiid,
                                                             final List<Integer> requestOrgans, final List<Integer> targetOrgans) {
        QueryMeetClinicHisDAO queryMeetClinicHisDAO = DAOFactory.getDAO(QueryMeetClinicHisDAO.class);
        return queryMeetClinicHisDAO.getStatisticsByExeStatus(startTime, endTime, mc, mr, start, mpiid, requestOrgans, targetOrgans);

    }

    @RpcService
    public HashMap<String, Integer> getStatisticsByRequestOrgan(final Date startTime, final Date endTime, final MeetClinic mc,
                                                                final MeetClinicResult mr, final int start, final String mpiid,
                                                                final List<Integer> requestOrgans, final List<Integer> targetOrgans) {
        QueryMeetClinicHisDAO queryMeetClinicHisDAO = DAOFactory.getDAO(QueryMeetClinicHisDAO.class);
        return queryMeetClinicHisDAO.getStatisticsByRequestOrgan(startTime, endTime, mc, mr, start, mpiid, requestOrgans, targetOrgans);

    }


    @RpcService
    public HashMap<String, Integer> getStatisticsByTargetOrgan(final Date startTime, final Date endTime, final MeetClinic mc,
                                                               final MeetClinicResult mr, final int start, final String mpiid,
                                                               final List<Integer> requestOrgans, final List<Integer> targetOrgans) {
        QueryMeetClinicHisDAO queryMeetClinicHisDAO = DAOFactory.getDAO(QueryMeetClinicHisDAO.class);
        return queryMeetClinicHisDAO.getStatisticsByTargetOrgan(startTime, endTime, mc, mr, start, mpiid, requestOrgans, targetOrgans);

    }

    @RpcService
    public QueryResult<Map> queryCloudMeetClic(final Date startTime, final Date endTime, final MeetClinic mc,
                                               final MeetClinicResult mr, final int start, final int limit, final String mpiId,
                                               final Integer requestOrgan, final Integer targetOrgan, final Integer offLineAccount) {
        if (startTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "startTime is require");
        }
        if (endTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "endTime is require");
        }

        final StringBuilder hql = new StringBuilder(
                " from MeetClinic mc,MeetClinicResult mr where mc.meetClinicId=mr.meetClinicId  and mc.requestTime>=:startTime and mc.requestTime<:endTime");
        final Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("startTime", startTime);
        parameters.put("endTime", DateConversion.getDateAftXDays(endTime, 1));

        if (requestOrgan != null) {
            hql.append(" and mc.requestOrgan =:requestOrgan");
            parameters.put("requestOrgan", requestOrgan);
        }
        if (targetOrgan != null) {
            hql.append(" and mr.targetOrgan =:targetOrgan");
            parameters.put("targetOrgan", targetOrgan);
        }
        if (!StringUtils.isEmpty(mpiId)) {
            hql.append(" and mc.mpiid=:mpiid");
            parameters.put("mpiid", mpiId);
        }
        if (offLineAccount != null) {
            if (offLineAccount == 0) {
                hql.append(" and mc.offLineAccount is null ");
            } else {
                hql.append(" and mc.offLineAccount =:offLineAccount");
                parameters.put("offLineAccount", offLineAccount);
            }

        }
        if (mc != null) {
            if (mc.getRequestDoctor() != null) {
                hql.append(" and mc.requestDoctor=:requestDoctor");
                parameters.put("requestDoctor", mc.getRequestDoctor());
            }
            if (mc.getMeetClinicStatus() != null) {
                hql.append(" and mc.meetClinicStatus=:meetClinicStatus");
                parameters.put("meetClinicStatus", mc.getMeetClinicStatus());
            }
            if (mc.getMeetClinicType() != null) {
                hql.append(" and mc.meetClinicType=:meetClinicType");
                parameters.put("meetClinicType", mc.getMeetClinicType());
            }
        }
        if (mr != null) {
            if (mr.getTargetDoctor() != null) {
                hql.append(" and mr.targetDoctor=:targetDoctor");
                parameters.put("targetDoctor", mr.getTargetDoctor());
            }
        }

        HibernateStatelessResultAction<QueryResult<Map>> action = new AbstractHibernateStatelessResultAction<QueryResult<Map>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query cQuery = ss.createQuery(" select count(distinct mc) " + hql);
                Query query = ss.createQuery("select distinct mc " + hql + " order By mc.meetClinicId desc");
                query.setFirstResult(start);
                query.setMaxResults(limit > 20 ? 20 : limit);
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    cQuery.setParameter(entry.getKey(), entry.getValue());
                    query.setParameter(entry.getKey(), entry.getValue());
                }
                Long total = (Long) cQuery.uniqueResult();
                List<MeetClinic> list = query.list();
                if (list == null || list.size() <= 0) {
                    setResult(new QueryResult<Map>(total, start, limit, null));
                } else {
                    MeetClinicResultDAO meetClinicResultDAO = DAOFactory.getDAO(MeetClinicResultDAO.class);
                    Map<Integer, Map> maps = new HashMap<Integer, Map>();
                    List<Integer> mcIds = new ArrayList<Integer>();
                    PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                    for (MeetClinic meetClinic : list) {
                        Map<String, Object> mcMap = new HashMap<String, Object>();
                        BeanUtils.map(meetClinic, mcMap);
                        mcMap.put("mrs", new ArrayList<MeetClinicResult>());
                        Patient patient = patientDAO.getByMpiId(meetClinic.getMpiid());
                        if (patient != null) {
                            mcMap.put("patientName", patient.getPatientName());
                            mcMap.put("patientMobile", patient.getPatientName());
                        }
                        maps.put(meetClinic.getMeetClinicId(), mcMap);
                        mcIds.add(meetClinic.getMeetClinicId());
                    }
                    List<MeetClinicResult> results = meetClinicResultDAO.findByMeetClinicIds(mcIds);
                    if (results != null) {
                        Iterator it = results.iterator();
                        while (it.hasNext()) {
                            MeetClinicResult result = (MeetClinicResult) it.next();
                            ((List<MeetClinicResult>) maps.get(result.getMeetClinicId()).get("mrs")).add(result);
                        }
                    }
                    List<Map> listMap = new ArrayList<Map>();
                    for (MeetClinic mc : list) {
                        listMap.add(maps.get(mc.getMeetClinicId()));

                    }
                   /* for (Map.Entry<Integer, Map> entry : maps.entrySet()) {
                        listMap.add(entry.getValue());
                    }*/
                    setResult(new QueryResult<Map>(total, start, limit, listMap));
                }
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 运营平台（权限改造）
     *
     * @param meetClinicId
     * @return
     */
    @RpcService
    public List<MeetClinicAndResult> getMeetClinicAndCdrOtherdoc(
            int meetClinicId) {

        MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        List<MeetClinicAndResult> mcrs = meetClinicDAO.getMeetClinicAndCdrOtherdoc(meetClinicId);
        if (mcrs == null || mcrs.isEmpty()) {
            return null;
        }
        Set<Integer> o = new HashSet<Integer>();
        for (MeetClinicAndResult mcr : mcrs) {
            o.add(mcr.getMc().getRequestOrgan());
            MeetClinicResult mr = mcr.getMr();
            o.add(mr.getTargetOrgan());
        }
        if (!SecurityService.isAuthoritiedOrgan(o)) {
            return null;
        }
        return mcrs;

    }

}
