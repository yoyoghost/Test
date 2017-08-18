package eh.util;

import ctd.persistence.DAOFactory;
import eh.base.dao.DoctorDAO;
import eh.base.dao.DoctorGroupDAO;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorGroup;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 判断医生和病人、申请医生和目标医生是否为同一人
 *
 * @author LF
 */
public class SameUserMatching {

    static DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
    static PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);

    /**
     * 咨询/预约/会诊
     *
     * @param mpiId
     * @param doctorId
     * @return
     * @author LF
     */
    public static Boolean patientAndDoctor(String mpiId, Integer doctorId) {
        //获取医生身份证号
        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        String idNumber = null;
        //新建List存放目标医生身份证
        List<String> idNumbers = new ArrayList<String>();
        if (StringUtils.isEmpty(doctor.getIdNumber())) {
            //判断目标医生是否为团队医生
            DoctorGroupDAO doctorGroupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
            List<DoctorGroup> doctorGroups = doctorGroupDAO.findByDoctorId(doctorId);
            for (int i = 0; i < doctorGroups.size(); i++) {
                doctor = doctorDAO.getByDoctorId(doctorGroups.get(i).getMemberId());
                if (StringUtils.isEmpty(doctor.getIdNumber())) {
                    //虚拟医生
                    continue;
                }
                idNumber = doctor.getIdNumber().toLowerCase();
                idNumbers.add(idNumber);
            }
        } else {
            idNumber = doctor.getIdNumber().toLowerCase();
            idNumbers.add(idNumber);
        }

        //获取病人身份证号
        Patient patient = patientDAO.get(mpiId);
        String idcard = StringUtils.isEmpty(patient.getIdcard()) ? null : patient.getIdcard().toLowerCase();
        String rawIdcard = StringUtils.isEmpty(patient.getRawIdcard()) ? null : patient.getRawIdcard().toLowerCase();
        if (idcard == null && rawIdcard == null) {
            return false;
        }
        //判断医生和病人身份证是否相同(身份证存15位和18位两个身份证，因此两个身份证都要做判断)
        if (idcard != null) {
            for (int i = 0; i < idNumbers.size(); i++) {
                if (idcard.equals(idNumbers.get(i))) {
                    return true;
                }
            }
        }

        if (rawIdcard != null) {
            for (int i = 0; i < idNumbers.size(); i++) {
                if (rawIdcard.equals(idNumbers.get(i))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 转诊会诊
     *
     * @param request
     * @param target
     * @return
     * @author LF
     */
    public static Boolean requestAndTarget(Integer request, Integer target) {
        //获取申请医生身份证号
        Doctor requestDoctor = doctorDAO.getByDoctorId(request);
        String rIdNumber = requestDoctor.getIdNumber().toLowerCase();

        //新建List存放目标医生身份证
        List<String> tIdNumbers = new ArrayList<String>();
        //获取目标医生身份证号
        Doctor targetDoctor = doctorDAO.getByDoctorId(target);
        String tIdNumber = null;
        if (StringUtils.isEmpty(targetDoctor.getIdNumber())) {
            //判断目标医生是否为团队医生
            DoctorGroupDAO doctorGroupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
            List<DoctorGroup> doctorGroups = doctorGroupDAO.findByDoctorId(target);
            for (int i = 0; i < doctorGroups.size(); i++) {
                targetDoctor = doctorDAO.getByDoctorId(doctorGroups.get(i).getMemberId());
                if (StringUtils.isEmpty(targetDoctor.getIdNumber())) {
                    //虚拟医生
                    continue;
                }
                tIdNumber = targetDoctor.getIdNumber().toLowerCase();
                tIdNumbers.add(tIdNumber);
            }
        } else {
            tIdNumber = targetDoctor.getIdNumber().toLowerCase();
            tIdNumbers.add(tIdNumber);
        }
        //判断申请医生和目标医生身份证是否相同
        for (int i = 0; i < tIdNumbers.size(); i++) {
            if (rIdNumber.equals(tIdNumbers.get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 会诊(添加执行单)
     *
     * @param request
     * @param target
     * @return
     * @author LF
     */
    public static Boolean targetAndTarget(Integer request, Integer target) {
        //新建List存放目标医生1身份证
        List<String> rIdNumbers = new ArrayList<String>();
        //获取目标医生1身份证号
        Doctor requestDoctor = doctorDAO.getByDoctorId(request);
        String rIdNumber = null;
        if (StringUtils.isEmpty(requestDoctor.getIdNumber())) {
            //判断目标医生1是否为团队医生
            DoctorGroupDAO doctorGroupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
            List<DoctorGroup> doctorGroups = doctorGroupDAO.findByDoctorId(request);
            for (int i = 0; i < doctorGroups.size(); i++) {
                requestDoctor = doctorDAO.getByDoctorId(doctorGroups.get(i).getMemberId());
                if (StringUtils.isEmpty(requestDoctor.getIdNumber())) {
                    //虚拟医生
                    continue;
                }
                rIdNumber = requestDoctor.getIdNumber().toLowerCase();
                rIdNumbers.add(rIdNumber);
            }
        } else {
            rIdNumber = requestDoctor.getIdNumber().toLowerCase();
            rIdNumbers.add(rIdNumber);
        }

        //新建List存放目标医生身份证
        List<String> tIdNumbers = new ArrayList<String>();
        //获取目标医生身份证号
        Doctor targetDoctor = doctorDAO.getByDoctorId(target);
        String tIdNumber = null;
        if (StringUtils.isEmpty(targetDoctor.getIdNumber())) {
            //判断目标医生是否为团队医生
            DoctorGroupDAO doctorGroupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
            List<DoctorGroup> doctorGroups = doctorGroupDAO.findByDoctorId(target);
            for (int i = 0; i < doctorGroups.size(); i++) {
                targetDoctor = doctorDAO.getByDoctorId(doctorGroups.get(i).getMemberId());
                if (StringUtils.isEmpty(targetDoctor.getIdNumber())) {
                    //虚拟医生
                    continue;
                }
                tIdNumber = targetDoctor.getIdNumber().toLowerCase();
                tIdNumbers.add(tIdNumber);
            }
        } else {
            tIdNumber = targetDoctor.getIdNumber().toLowerCase();
            tIdNumbers.add(tIdNumber);
        }
        //判断申请医生和目标医生身份证是否相同
        for (int i = 0; i < tIdNumbers.size(); i++) {
            for (int j = 0; j < rIdNumbers.size(); j++) {
                if ((rIdNumbers.get(j)).equals(tIdNumbers.get(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断患者/申请人是否与医生为同一个人，
     *
     * @param mpiId      患者
     * @param requestMpi 申请人
     * @param doctorId   医生id
     * @return
     */
    public static HashMap<String, Boolean> patientsAndDoctor(String mpiId, String requestMpi, Integer doctorId) {
        HashMap<String, Boolean> returnMap = new HashMap<String, Boolean>();

        //获取患者身份证
        List<String> patIdNums = new ArrayList<String>();
        if (!StringUtils.isEmpty(mpiId)) {
            Patient pat = patientDAO.get(mpiId);
            if (pat != null) {
                String patIdNum = pat.getIdcard();
                String rawPatIdNum = pat.getRawIdcard();
                if (!StringUtils.isEmpty(patIdNum)) {
                    patIdNums.add(patIdNum.toLowerCase());
                }
                if (!StringUtils.isEmpty(rawPatIdNum)) {
                    patIdNums.add(rawPatIdNum.toLowerCase());
                }
            }
        }


        //获取申请人身份证
        List<String> reqPatIdNums = new ArrayList<String>();
        if (!StringUtils.isEmpty(requestMpi)) {
            Patient reqPat = patientDAO.get(requestMpi);
            if (reqPat != null) {
                String reqPatIdNum = reqPat.getIdcard();
                String rawReqPatIdNum = reqPat.getRawIdcard();
                if (!StringUtils.isEmpty(reqPatIdNum)) {
                    reqPatIdNums.add(reqPatIdNum.toLowerCase());
                }
                if (!StringUtils.isEmpty(rawReqPatIdNum)) {
                    reqPatIdNums.add(rawReqPatIdNum.toLowerCase());
                }
            }
        }

        //新建List存放目标医生身份证
        List<String> idNumbers = new ArrayList<String>();
        Doctor doctor = doctorDAO.get(doctorId);
        String idNumber = null;
        boolean teams = false;
        if (doctor != null) {
            if (StringUtils.isEmpty(doctor.getIdNumber())) {
                //判断目标医生是否为团队医生
                DoctorGroupDAO doctorGroupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
                List<DoctorGroup> doctorGroups = doctorGroupDAO.findByDoctorId(doctorId);
                if (null != doctorGroups && !doctorGroups.isEmpty()) {
                    teams = true;
                    for (int i = 0; i < doctorGroups.size(); i++) {
                        doctor = doctorDAO.getByDoctorId(doctorGroups.get(i).getMemberId());
                        if (doctor == null || StringUtils.isEmpty(doctor.getIdNumber())) {
                            //虚拟医生
                            continue;
                        }
                        idNumber = doctor.getIdNumber().toLowerCase();
                        if (!idNumbers.contains(idNumber)) {
                            idNumbers.add(idNumber);
                        }
                    }
                }
            } else {
                idNumber = doctor.getIdNumber().toLowerCase();
                idNumbers.add(idNumber);
            }
        }


        //判断患者是否和申请人为同一个人
        Boolean patSameWithReqPat = false;
        for (String id : patIdNums) {
            if (reqPatIdNums.contains(id)) {
                patSameWithReqPat = true;
            }
        }

        //判断患者是否和医生为同一个人
        Boolean patSameWithDoc = false;
        for (String id : patIdNums) {
            if (idNumbers.contains(id)) {
                patSameWithDoc = true;
            }
        }

        //判断申请人是否和医生为同一个人
        Boolean reqPatSameWithDoc = false;
        for (String id : reqPatIdNums) {
            if (idNumbers.contains(id)) {
                reqPatSameWithDoc = true;
            }
        }

        Boolean beTeams = false;
        //患者是和申请人为同一个人,且团队里只有患者的医生账户
        if (patSameWithReqPat && patSameWithDoc && reqPatSameWithDoc && 1 == idNumbers.size()) {
            beTeams = true;
        }
        //患者是和申请人为两个人,且团队里只有申请人和患者的医生账户
        if ((!patSameWithReqPat) && patSameWithDoc && reqPatSameWithDoc && 2 == idNumbers.size()) {
            beTeams = true;
        }

        returnMap.put("patSameWithDoc", patSameWithDoc);//患者是否和医生为同一个人,true为同一个人
        returnMap.put("reqPatSameWithDoc", reqPatSameWithDoc);//判断申请人是否和医生为同一个人,true为同一个人
        returnMap.put("beTeams", beTeams);//判断申请人和患者是否组成了医生团队，该团队只有申请人和患者,true为是
        returnMap.put("teams", teams);//目标医生是否为团队医生

        return returnMap;
    }
}
