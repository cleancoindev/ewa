package com.credits.service.contract;

import com.credits.exception.ContractExecutorException;
import com.credits.general.pojo.ByteCodeObjectData;
import com.credits.general.thrift.generated.Variant;
import com.credits.service.ServiceTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.credits.general.thrift.generated.Variant._Fields.V_INT;
import static com.credits.general.thrift.generated.Variant._Fields.V_STRING;
import static java.io.File.separator;
import static org.junit.Assert.fail;
import static org.junit.runners.Parameterized.Parameter;
import static org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SecurityTest extends ServiceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityTest.class);

    private final static String prjDir = "\"" + System.getProperty("user.dir") + separator + "credits" + separator + "file.test" + "\"";

    @Parameter
    public String methodName;
    @Parameter(1)
    public Variant arg;
    @Parameter(2)
    public boolean errorExpected;

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {"getBalance", null, false},
            {"openSocket",  new Variant(V_INT, 5555), false},
            {"setTotal", new Variant(V_INT, 1000), false},
            {"getTotal", null, false},
            {"createFile", null, false},
            {"createFileInProjectDir", new Variant(V_STRING, prjDir), false},
            {"killProcess", null, false},
            {"newThread", null, false},
        });
    }

    List<ByteCodeObjectData> byteCodeObjectData;
    byte[] contractState;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        String sourceCode = "/securityTest/Contract.java";
        byteCodeObjectData = compileSourceCode(sourceCode);

        contractState = ceService.execute(address, byteCodeObjectData, null, null, null,500L).getContractState();
    }

    @Test
    public void test() {
        try {
            ceService.execute(address,
                byteCodeObjectData, contractState, methodName, arg != null ? new Variant[][] {{arg}} : new Variant[][]{{}},500L);
        } catch (ContractExecutorException e) {
            LOGGER.error(e.getMessage());
            return;
        }
        if (errorExpected) {
            fail("error expected");
        }
    }
}
