component {
    property name="isAProperty";

    variables.inVariables = "something";
    inVariablesNoScope = "something";
    variables[ "inVariablesArrayAccess" ] = "something";

    public function thing(){
        var x = 4;
        foo = "test";
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
}