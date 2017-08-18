package eh.cdr.service;

import ctd.util.AppContextHolder;
import eh.entity.cdr.CommonRecipe;
import eh.entity.cdr.CommonRecipeDrug;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jiangtingfeng on 2017/5/27.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring-test.xml")
public class CommonRecipeServiceTest extends AbstractJUnit4SpringContextTests
{
    @Test
    public void addCommonRecipe()
    {
        CommonRecipeService commonRecipeService = AppContextHolder.getBean("eh.commonRecipeService",CommonRecipeService.class);
        CommonRecipe commonRecipe = new CommonRecipe();
        List list = new ArrayList<>();

        String str = "胖子专用方";
        commonRecipe.setRecipeType("1");
        commonRecipe.setOrganId(1);
        commonRecipe.setDoctorId(10087);
        commonRecipe.setCommonRecipeName(str);

        CommonRecipeDrug commonRecipeDrug = new CommonRecipeDrug();
        commonRecipeDrug.setDrugId(11);
        commonRecipeDrug.setDrugSpec("111111");
        commonRecipeDrug.setDrugUnit("111111");
        commonRecipeDrug.setDrugName("1111111111111111111");
        list.add(commonRecipeDrug);

        CommonRecipeDrug commonRecipeDrug1 = new CommonRecipeDrug();
        commonRecipeDrug1.setDrugId(12);
        commonRecipeDrug1.setDrugSpec("222222");
        commonRecipeDrug1.setDrugUnit("222222");
        commonRecipeDrug1.setDrugName("22222222222222222222");
        list.add(commonRecipeDrug1);

        CommonRecipeDrug commonRecipeDrug2 = new CommonRecipeDrug();
        commonRecipeDrug2.setDrugId(13);
        commonRecipeDrug2.setDrugSpec("333333");
        commonRecipeDrug2.setDrugUnit("333333");
        commonRecipeDrug2.setDrugName("333333333333333333333");
        list.add(commonRecipeDrug2);

        logger.info("list [] :" + list.toString() +"commonRecipe is "+ commonRecipe.toString());
        commonRecipeService.addCommonRecipe(commonRecipe,list);
    }

    @Test
    public void deleteCommonRecipe()
    {
        CommonRecipeService commonRecipeService = AppContextHolder.getBean("eh.commonRecipeService",CommonRecipeService.class);

        Integer commonRecipeId = 2;
        commonRecipeService.deleteCommonRecipe(commonRecipeId);
    }

    @Test
    public void checkCommonRecipeExist()
    {
        CommonRecipeService commonRecipeService = AppContextHolder.getBean("eh.commonRecipeService",CommonRecipeService.class);
        String recipeType = "1";
        Integer doctorId = 10086;
        Boolean result = commonRecipeService.checkCommonRecipeExist(doctorId,recipeType);
        System.out.println(result);
    }

    @Test
    public void getCommonRecipeList()
    {
        CommonRecipeService commonRecipeService = AppContextHolder.getBean("eh.commonRecipeService",CommonRecipeService.class);
        List<CommonRecipe> list = commonRecipeService.getCommonRecipeList(1,9561,"0",0,10);

        System.out.println(list.toString());
    }

    @Test
    public void getCommonRecipeDetails()
    {
        CommonRecipeService commonRecipeService = AppContextHolder.getBean("eh.commonRecipeService",CommonRecipeService.class);
        System.out.println(commonRecipeService.getCommonRecipeDetails(15));
    }
}
