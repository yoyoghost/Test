package eh.util;

import ctd.persistence.DAOFactory;
import eh.base.dao.OrganDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;

import java.util.*;

/**
 * Created by andywang on 2016/11/30.
 */
public class DoctorUtil {

    public static  HashMap<Integer, Doctor> convertDoctorListToHash(List<Doctor> doctors)
    {
        HashMap<Integer, Doctor> converted = new HashMap<Integer, Doctor>();
        Iterator<Doctor> iterTemp = doctors.iterator();
        while (iterTemp.hasNext()) {
            Doctor r = iterTemp.next();
            converted.put(r.getDoctorId(), r);
        }
        return converted;
    }

    public static HashMap<String, Integer> translateOrganHash(HashMap<Integer, Integer> map)
    {
        if (map != null && !map.isEmpty())
        {
            List<Integer> organIdsTemp = new ArrayList<Integer>();
            Iterator iter = map.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry entry = (Map.Entry) iter.next();
                Integer organID = Integer.parseInt(entry.getKey().toString());
                organIdsTemp.add(organID);
            }
            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            List<Organ> organs = organDAO.findOrgansByOrganIds(organIdsTemp);
            HashMap<Integer, Organ> mapOrgans = organDAO.convertToHashMapKeyWithOrganId(organs);
            HashMap<String, Integer> mapOrganStatistics = new HashMap<String, Integer>();

            Iterator iter2 = map.entrySet().iterator();
            while (iter2.hasNext()) {
                Map.Entry entry = (Map.Entry) iter2.next();
                Integer organID = Integer.parseInt(entry.getKey().toString());
                if(organID == 0)
                {
                    mapOrganStatistics.put("", Integer.valueOf(entry.getValue().toString()));
                }
                else
                {
                    Organ o = mapOrgans.get(organID);
                    if (o != null)
                    {
                        mapOrganStatistics.put(o.getShortName(), Integer.valueOf(entry.getValue().toString()));
                    }
                    else
                    {
                        mapOrganStatistics.put(organID.toString(), Integer.valueOf(entry.getValue().toString()));
                    }
                }

            }
            return mapOrganStatistics;
        }
        else
        {
            return null;
        }
    }
}
