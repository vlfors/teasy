package com.wiley.autotest.selenium.elements.upgrade.v3.conditions;

import com.wiley.autotest.ExpectedConditions2;
import com.wiley.autotest.selenium.elements.upgrade.OurWebElement;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.function.Function;

/**
 * Created by vefimov on 25/05/2017.
 */
public class FirstFoundInContext implements OurCondition {

    private OurWebElement context;

    public FirstFoundInContext(OurWebElement context) {
        this.context = context;
    }

    @Override
    public Function<WebDriver, List<WebElement>> visibility(By locator) {
        return ExpectedConditions2.visibilityOfFirstElements(context, locator);
    }

    @Override
    public Function<WebDriver, List<WebElement>> presence(By locator) {
        return ExpectedConditions2.presenceOfAllElementsLocatedBy(context, locator);
    }
}
