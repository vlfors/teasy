package com.wiley.autotest.services;

import com.wiley.autotest.annotations.*;
import com.wiley.autotest.selenium.AbstractSeleniumTest;
import com.wiley.autotest.selenium.AbstractTest;
import com.wiley.autotest.selenium.Group;
import com.wiley.autotest.selenium.SeleniumHolder;
import com.wiley.autotest.utils.DriverUtils;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.testng.ITestContext;
import org.testng.TestRunner;
import org.testng.annotations.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static java.lang.String.format;
import static org.testng.Reporter.log;

/**
 * User: dfedorov
 * Date: 7/26/12
 * Time: 9:32 AM
 */
@Service
public class SeleniumMethodsInvoker extends MethodsInvoker {

    private static final String FIREFOX = "firefox";
    private static final String CHROME = "chrome";
    private static final String SAFARI = "safari";
    private static final String IE = "ie";
    private static final String IOS = "ios";
    private static final String ANDROID = "android";
    private static final String WINDOWS = "windows";
    private static final String MAC = "mac";

    private CookiesService cookiesService;

    @Autowired
    public void setCookiesService(CookiesService cookiesService) {
        this.cookiesService = cookiesService;
    }

    private static ThreadLocal<Boolean> isFFDriver = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    public <T extends Annotation> void invokeSuiteMethodsByAnnotation(final Class<T> annotationClass, final ITestContext testContext) {
        invokeGroupMethodsByAnnotation(annotationClass, testContext);
    }

    public <T extends Annotation> void invokeGroupMethodsByAnnotation(final Class<T> annotationClass, final ITestContext testContext) {
        initialize();
        final TestClassContext testClassContext = new TestClassContext(((TestRunner) testContext).getTest().getXmlClasses().get(0).getSupportClass(), null, annotationClass, testContext);
        invokeMethodsByAnnotation(testClassContext, true);
    }

    public <T extends Annotation> void invokeMethodsByAnnotation(final AbstractSeleniumTest testObject, final Class<T> annotationClass) {
        invokeMethodsByAnnotation(new TestClassContext(testObject.getClass(), testObject, annotationClass), false);
    }

    private void initialize() {
        if (cookiesService == null) {
            cookiesService = new CookiesService();
        }
    }

    @Override
    void invokeMethod(AbstractTest instance, Method method, TestClassContext context, boolean isBeforeAfterGroup) {
        final WebDriver mainDriver = SeleniumHolder.getWebDriver();
        final String mainDriverName = SeleniumHolder.getDriverName();
        String platformName = SeleniumHolder.getPlatform();

        AbstractSeleniumTest abstractSeleniumTest = (AbstractSeleniumTest) instance;

        if (abstractSeleniumTest.getTestMethod() != null && isSkippedTest(abstractSeleniumTest)) {
            return;
        }

        //For IE and Safari browser and @FireFoxOnly annotation setting specifically prepared FF driver
        if ((mainDriverName.contains(IE) ||
                mainDriverName.equals(SAFARI) ||
                platformName.equals(ANDROID))
                && method.getAnnotation(FireFoxOnly.class) != null) {
            SeleniumHolder.setWebDriver(DriverUtils.getFFDriver());
            SeleniumHolder.setDriverName(FIREFOX);
            isFFDriver.set(true);
        }

        //Set FirefoxOnly for all after methods in safari, ie, android
        if ((mainDriverName.equals(SAFARI) ||
                mainDriverName.contains(IE) ||
                platformName.equals(ANDROID)) &&
                (method.getAnnotation(OurAfterMethod.class) != null ||
                        method.getAnnotation(OurAfterClass.class) != null ||
                        method.getAnnotation(OurAfterGroups.class) != null ||
                        method.getAnnotation(OurAfterSuite.class) != null)) {
            SeleniumHolder.setWebDriver(DriverUtils.getFFDriver());
            SeleniumHolder.setDriverName(FIREFOX);
            isFFDriver.set(true);
        }

        try {
            method.invoke(instance);
        } catch (Throwable e) {
            abstractSeleniumTest.takeScreenshot(e.getMessage(), method.getName());
            final Writer result = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(result);
            ((InvocationTargetException) e).getTargetException().printStackTrace(printWriter);
            String errorMessage = format("Precondition method '%s' failed ", method.getName()) + "\n " +
                    result.toString();
            if (isBeforeAfterGroup) {
                abstractSeleniumTest.setPostponedBeforeAfterGroupFail(errorMessage, context.getTestContext());
            } else {
                abstractSeleniumTest.setPostponedTestFail(errorMessage);
            }

            if (method.getAnnotation(RetryPrecondition.class) != null) {
                retryCount.set(retryCount.get() + 1);
                if (method.getAnnotation(RetryPrecondition.class).retryCount() >= retryCount.get()) {
                    LOGGER.error("*****ERROR***** Method '" + method.getDeclaringClass().getSimpleName() + "." + method.getName() + "' is failed. Retrying it. Current retryCount is " + retryCount.get());
                    invokeMethod(instance, method, context, isBeforeAfterGroup);
                }
            }

            log(errorMessage);

            if (method.getAnnotation(RetryPrecondition.class) == null) {
                throw new StopTestExecutionException(errorMessage, e);
            }
        } finally {
            if (isFFDriver.get() && mainDriverName.equals(FIREFOX)) {
                SeleniumHolder.getWebDriver().quit();
            }
            SeleniumHolder.setDriverName(mainDriverName);
            SeleniumHolder.setWebDriver(mainDriver);
        }
    }

    public boolean isSkippedTest(AbstractSeleniumTest instance) {
        Method method = instance.getTestMethod();
        String platform = SeleniumHolder.getPlatform();
        String driverName = SeleniumHolder.getDriverName();

        if (platform.equals(ANDROID) && isNoGroupTest(method, Group.noAndroid)) {
            return true;
        }

        if (platform.equals(IOS) && isNoGroupTest(method, Group.noIos)) {
            return true;
        }

        if (platform.equals(WINDOWS) && isNoGroupTest(method, Group.noWindows)) {
            return true;
        }

        if (platform.equals(MAC) && isNoGroupTest(method, Group.noMac)) {
            return true;
        }

        if (driverName.equals(CHROME) && isNoGroupTest(method, Group.noChrome)) {
            return true;
        }

        if (driverName.equals(SAFARI) && isNoGroupTest(method, Group.noSafari)) {
            return true;
        }

        if (driverName.contains(IE) && isNoGroupTest(method, Group.noIE)) {
            return true;
        }

        if (driverName.equals(FIREFOX) && isNoGroupTest(method, Group.noFF)) {
            return true;
        }

        return false;
    }

    private boolean isNoGroupTest(Method method, String noGroupName) {
        String[] groups = method.getAnnotation(Test.class).groups();
        for (String group : groups) {
            if (group.equals(noGroupName)) {
                return true;
            }
        }
        return false;
    }
}
