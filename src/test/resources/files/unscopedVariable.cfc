component {
    property name="isAProperty";

    variables.inVariables = "something";
    inVariablesNoScope = "something";
    variables[ "inVariablesArrayAccess" ] = "something";

    public function thing(){
        var x = 4;
        foo = "test";

        x = 5;
    }

    public function checkProperty(){
        isAProperty = "test";
    }

    public function checkMultiple(){
        multiple = "test";
        multiple = "test";
    }

    public function checkFromVariables(){
        inVariables = "test";
        inVariablesNoScope = "test";
        inVariablesArrayAccess = "test";
    }

    public function checkMultipleShouldWarn(){
        shouldWarnOnce = "test";
        shouldWarnOnce = "test";
        shouldWarnOnce = "test";
    }

    public function checkWasVard(){
        var hasBeenVard = "test";
        hasBeenVard = "new value";
    }

    public function checkWasVardByArgument( someArg ){
        someArg = "new value";
        var what = someArg + 2;
    }

    public function test( theUnusedArg ){
        var theUnusedVar = "test";

        // var x = theUnusedVar;
    
        // x.ucase();

       
    }
}