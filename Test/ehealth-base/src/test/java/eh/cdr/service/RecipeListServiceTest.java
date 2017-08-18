package eh.cdr.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.dao.EmploymentDAO;
import eh.bus.dao.ConsultDAO;
import eh.entity.base.Doctor;
import eh.entity.bus.Consult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2017/2/13.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring.xml")
public class RecipeListServiceTest extends AbstractJUnit4SpringContextTests {

    @Test
    public void findRecipesForDoctor() throws Exception {
        RecipeListService recipeListService = AppContextHolder.getBean("eh.recipeListService", RecipeListService.class);
        show(recipeListService.findRecipesForDoctor(9559,0,2));
    }

    @Test
    public void dealWithConsultsByRequestMode(){
        ConsultDAO consultDAO = AppContextHolder.getBean("eh.consult",ConsultDAO.class);
        System.out.println(2);
        List<Consult> consults = consultDAO.findConsultListByRequestMpiAndRequestMode("2c90818954854b6401548579a8890001",1,2);
        System.out.println(1);
        for(Consult c : consults){
            System.out.println(JSONUtils.toString(c));
        }
    }

    @Test
    public void getLastestPendingRecipe() throws Exception {
        RecipeListService recipeListService = AppContextHolder.getBean("eh.recipeListService", RecipeListService.class);
        show(recipeListService.getLastestPendingRecipe("2c90818253e5268d0153e55735e10000"));
    }

    @Test
    public void findLastesRecipeList() throws Exception {
        RecipeListService recipeListService = AppContextHolder.getBean("eh.recipeListService", RecipeListService.class);
        show(recipeListService.findLastesRecipeList(5));
    }

    @Test
    public void findOtherRecipesForPatient() throws Exception {
        RecipeListService recipeListService = AppContextHolder.getBean("eh.recipeListService", RecipeListService.class);
        show(recipeListService.findOtherRecipesForPatient("2c90818954c1623a0154c1a562410000",0,2));
    }

    @Test
    public void findDoctorByCount() {
        RecipeListService recipeListService = AppContextHolder.getBean("eh.recipeListService", RecipeListService.class);
        show(recipeListService.findDoctorByCount(0, 6));
    }


    public void show(Object object) {
        Assert.notNull(object,"object can't be null...");
        System.out.println(JSONUtils.toString(object));
    }

    @Test
    public void testFindEffEmpWithDrug() {
        EmploymentDAO empDAO = DAOFactory.getDAO(EmploymentDAO.class);
        List<HashMap<String, Object>> list = empDAO.findEffEmpWithDrug(9571);
        System.out.println(JSONUtils.toString(list));
    }



}