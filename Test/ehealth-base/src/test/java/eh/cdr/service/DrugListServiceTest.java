package eh.cdr.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.service.DrugListService;
import eh.base.service.doctor.QueryDoctorListService;
import eh.bus.dao.SearchContentDAO;
import eh.bus.service.UnLoginSevice;
import eh.cdr.dao.DispensatoryDAO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by zhongzx on 2017/2/17 0017.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring.xml")
public class DrugListServiceTest extends AbstractJUnit4SpringContextTests {

    @Test
    public void test(){
        UnLoginSevice service = AppContextHolder.getBean("unLoginSevice", UnLoginSevice.class);
        System.out.println(JSONUtils.toString(service.searchDrugByNameOrPyCode("托", "", 0, 10)));
    }

    @Test
    public void getByDrugId(){
        DispensatoryService service = AppContextHolder.getBean("dispensatoryService", DispensatoryService.class);
        showInfo(service.getByDrugId(5));
    }

    @Test
    public void getByPageIdAndSource(){
        DispensatoryDAO dispensatoryDAO = DAOFactory.getDAO(DispensatoryDAO.class);
        showInfo(dispensatoryDAO.getByPageIdAndSource("4183",2));
    }

    private void showInfo(Object o){
        System.out.println(JSONUtils.toString(o));
    }

    @Test
    public void searchDoctorConsultOrCanRecipeExt(){
        UnLoginSevice service = AppContextHolder.getBean("unLoginSevice", UnLoginSevice.class);
        Map<String, Object> map = new HashMap<>();
        map.put("drugId", 1);
        showInfo(service.searchDoctorConsultOrCanRecipeExt("", "33", 1, "1301", "", "", 0, 2, 0, 2,map));
    }

    @Test
    public void findValidDepartmentByProfessionCodeExt(){
        UnLoginSevice service = AppContextHolder.getBean("unLoginSevice", UnLoginSevice.class);
        Map<String, Object> map = new HashMap<>();
        map.put("mark", 2);
        showInfo(service.findValidDepartmentByProfessionCodeExt("3301", null, null, map));
    }

    @Test
    public void queryDrugCatalogForDoctor(){
        DrugListService service = AppContextHolder.getBean("drugListService", DrugListService.class);
        showInfo(service.queryDrugCatalogForDoctor(1,1));
    }

    @Test
    public void searchDoctorConsultOrCanRecipeByDoctorName(){
        UnLoginSevice service = AppContextHolder.getBean("unLoginSevice", UnLoginSevice.class);
        Map<String, Object> map = new HashMap<>();
        showInfo(service.searchDoctorConsultOrCanRecipeByDoctorName("", "", 1, "", "", "", 0, 2, 0, 2,map, "刘"));
    }

    @Test
    public void testGetHomePageTaskForPatient(){
        RecipeService recipeService = AppContextHolder.getBean("recipeService", RecipeService.class);
        showInfo(recipeService.getHomePageTaskForPatient("2c90818253e5268d0153e55735e1000"));
    }

    @Test
    public void testFindContentsByMpiIdWithBuss(){
        SearchContentDAO dao = DAOFactory.getDAO(SearchContentDAO.class);
        showInfo(dao.findContentsByMpiIdWithBuss("2c908182598c5eb901598c64b6ea0000", 2));
    }

    @Test
    public void testeSarchDoctorListForConduct(){
        QueryDoctorListService service = AppContextHolder.getBean("eh.queryDoctorListService", QueryDoctorListService.class);
        showInfo(service.searchDoctorListForConduct("", 1, "", "", 0, 10));
    }
}
