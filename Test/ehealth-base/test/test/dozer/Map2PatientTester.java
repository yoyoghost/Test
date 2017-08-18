package test.dozer;

import java.io.InputStream;
import java.util.Map;



import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import eh.entity.mpi.Patient;
import junit.framework.TestCase;

public class Map2PatientTester extends TestCase {

	public void testCreate(){
		InputStream is = this.getClass().getClassLoader().getResourceAsStream("test/dozer/bean.json");
		
		Map map = JSONUtils.parse(is, Map.class);
		
		Patient p = BeanUtils.map(map, Patient.class);
		
		assertEquals(1,p.getHealthCards().size());
		
		System.out.println(JSONUtils.toString(p));
	}

}
