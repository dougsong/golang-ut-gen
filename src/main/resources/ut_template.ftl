package ${packageName}

import (
    "context"
    "reflect"
    "testing"

    . "github.com/bytedance/mockey"
    "github.com/stretchr/testify/assert"
)

<#list unitTests as ut>
func Test<#if ut.interfaceFunc>${ut.receiver.type}_${ut.funcName}<#else>${ut.testFuncName}</#if>(t *testing.T) {
    type args struct {
    <#list ut.args as arg>
        ${arg.name} ${arg.type}
    </#list>
    }

    type mocks struct {
        // TODO add your mock data
    }

    type wants struct {
    <#list ut.wants as want>
        ${want.name} ${want.type}
    </#list>
    }

    testcases := []struct {
        name  string
        args  args
        mocks mocks
        wants wants
    }{
        // TODO add your test case
    }

    for _, tt := range testcases {
        PatchConvey(tt.name, t, func() {
            // TODO add your mocks
            //Mock(GetPrivateMethod(launchTemplateDao, "GetByIdOrName")).
            //	Return(tt.mocks.mockLaunchTemplate, tt.mocks.mockGetByIdOrNameErr).Build()

            <#if ut.wants?? && (ut.wants?size > 0)>
            <#list ut.wants as want>${want.name}<#if want_has_next>, </#if></#list>:= <#if ut.interfaceFunc>(&${ut.receiver.type}{}).${ut.funcName}<#else>${ut.funcName}</#if>(<#list ut.args as arg>tt.args.${arg.name}<#if arg_has_next>, </#if></#list>)
            <#else>
            <#if ut.interfaceFunc>(&${ut.receiver.type}{}).${ut.funcName}<#else>${ut.funcName}</#if>(<#list ut.args as arg>tt.args.${arg.name}<#if arg_has_next>, </#if></#list>)
            </#if>

            <#list ut.wants as want>
                <#if want.type == "error">
            if tt.wants.err != nil {
                assert.NotNil(t, err)
                assert.Equal(t, tt.wants.err.Error(), err.Error())
            } else {
                assert.Nil(t, err)
            }
                <#elseif want.basicType>
            assert.Equal(t, tt.wants.${want.name}, ${want.name})
                <#else>
            assert.True(t, reflect.DeepEqual(tt.wants.${want.name}, ${want.name}))
                </#if>
            </#list>
        })
    }
}

</#list>