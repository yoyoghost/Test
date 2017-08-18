package eh.bus.service;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.bus.dao.AppointSourceDAO;
import eh.entity.base.Doctor;
import eh.entity.bus.AppointSource;
import eh.entity.bus.DoctorDateSource;
import eh.utils.DateConversion;

import java.util.*;

public class AppointSourceService {

    @RpcService
    public List<Date> getWorkDateList(Integer doctorId, Date date) {
        AppointSourceDAO sourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
        Date startDate = DateConversion.firstDayOfMonthForDate(date);
        Date endDate = DateConversion.lastDayOfMonthForDate(date);

        List<Date> list = sourceDAO.findWorkDateListByDoctorId(doctorId, startDate, endDate);
        return list;
    }

    /**
     * 可用接诊时段号源查询服务-去除tab-增加挂号科室入参
     *
     * @param inAddrArea     接诊方机构属地区域
     * @param inOrganId      接诊方机构内码
     * @param outDoctorId    就诊方医生内码
     * @param outWorkDate    就诊方号源日期
     * @param workType       号源日期类型
     * @param doctorId       当前登录医生内码
     * @param professionCode 挂号科室
     * @return
     * @author LF 2016-08-28
     */
    @RpcService
    public List<HashMap<String, Object>> queryCloudWithoutTabWithProfessionCode(String inAddrArea,
                                                                                Integer inOrganId, Integer outDoctorId,
                                                                                Date outWorkDate, Integer workType, int doctorId, String professionCode) {
        AppointSourceDAO sourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
        List<HashMap<String, Object>> results = new ArrayList<HashMap<String, Object>>();
        //出诊方号源
        List<AppointSource> datas = sourceDAO.findAppointSourceWithProfessionCode(outDoctorId,
                outWorkDate, workType, professionCode);
        //过滤当天当前时间一小时前的号源
        Date time = DateConversion.getDateAftHour(new Date(), 1);
        List<AppointSource> datasRes = new ArrayList<AppointSource>();
        for (AppointSource s : datas) {
            int cloudclinic = s.getCloudClinic();
            // 去除当天号源提前一小时的限定
//            if (cloudclinic == 1 && !s.getStartTime().before(time)) {
            if (cloudclinic == 1) {
                datasRes.add(s);
            }
        }
        if (datasRes == null || datasRes.isEmpty()) {
            return results;
        }
        //出诊方starttime
        List<Date> dates = new ArrayList<Date>();
        for (AppointSource data : datasRes) {
            dates.add(data.getStartTime());
        }
        //匹配的接诊方号源及医生信息-出参排序=其次排序：按时间降序；再次排序：按首字母降序
        List<Object[]> appointSourceAndDoctor = new ArrayList<Object[]>();
        if (inOrganId != null) {
            appointSourceAndDoctor = sourceDAO.findAppointSourceCloudWithoutTab(inOrganId,
                    outDoctorId, dates);
        } else {
            appointSourceAndDoctor = sourceDAO.findAppointSourceCloudAreaWithoutTab(inAddrArea,
                    outDoctorId, dates);
        }

        List<HashMap<String, Object>> myself = new ArrayList<HashMap<String, Object>>();
        List<HashMap<String, Object>> inSources = new ArrayList<HashMap<String, Object>>();
        List<HashMap<String, Object>> outSources = new ArrayList<HashMap<String, Object>>();
        List<AppointSource> datasResHaveIn = new ArrayList<AppointSource>();
        for (Object[] os : appointSourceAndDoctor) {
            AppointSource as = (AppointSource) os[0];
            Date start = as.getStartTime();
            Doctor d = (Doctor) os[1];
            Integer docId = d.getDoctorId();
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("inSource", as);
            map.put("doctor", d);
            boolean isMyself = false;
            for (AppointSource data : datasRes) {
                Date startT = data.getStartTime();
                if (start.equals(startT)) {
                    map.put("outSource", data);
                    datasResHaveIn.add(data);
                    break;
                }
            }
            if (docId.equals(doctorId)) {
                isMyself = true;
                map.put("isMyself", isMyself);
                myself.add(map);
            } else {
                map.put("isMyself", isMyself);
                inSources.add(map);
            }
        }
        datasRes.removeAll(datasResHaveIn);
        for (AppointSource data : datasRes) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("outSource", data);
            outSources.add(map);
        }

        results.addAll(myself);
        results.addAll(inSources);
        results.addAll(outSources);

        return results;
    }

    /**
     * 查询医生日期号源服务--增加是否剔除云门诊
     *
     * @param doctorId   医生编号
     * @param sourceType 号源类别
     * @param flag       是否剔除云门诊-0不剔除1剔除
     * @return List<HashMap<String, Object>>
     * @throws ControllerException
     * @date 2017-04-27 luf 仅医生端转诊入口剔除云门诊
     */
    @RpcService
    public List<HashMap<String, Object>> searchDoctorDateSourcesWithFlag(int doctorId,
                                                                         int sourceType, int flag) throws ControllerException {
        AppointSourceDAO appointSourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
        if (flag == 0) {
            return appointSourceDAO.convertTotalForIOS(doctorId, sourceType);
        }
        List<DoctorDateSource> dateSources = appointSourceDAO.searchDoctorDateSourcesWithoutCloud(doctorId, sourceType);
        List<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

        for (DoctorDateSource dateSource : dateSources) {
            Map<String, Object> object = new HashMap<String, Object>();
            List<DoctorDateSource> sources = new ArrayList<DoctorDateSource>();
            Integer oragnID = dateSource.getOragnID();
            String professionCode = dateSource.getProfessionCode();
            int mach = -1;
            for (int i = 0; i < list.size(); i++) {
                Map<String, Object> hashMap = list.get(i);
                if (oragnID.equals(hashMap.get("oragnID"))
                        && professionCode.equals(hashMap.get("professionCode"))) {
                    mach = i;
                    break;
                }
            }
            if (mach >= 0) {
                sources = (List<DoctorDateSource>) list.get(mach).get(
                        "dateSource");
                sources.add(dateSource);
                (list.get(mach)).put("dateSource", sources);
            } else {
                String organName = DictionaryController.instance()
                        .get("eh.base.dictionary.Organ").getText(oragnID);
                String professionName = dateSource.getProfessionName();
                sources.add(dateSource);
                object.put("oragnID", oragnID);
                object.put("organName", organName);
                object.put("professionCode", professionCode);
                object.put("professionName", professionName);
                object.put("dateSource", sources);
                list.add((HashMap<String, Object>) object);
            }
        }
        return list;
    }
}
