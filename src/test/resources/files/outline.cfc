component {
    property name="propA" type="string" default="valueA";
    property name="propB" type="string" default="valueB";

    variables.what = "something";
    variables.age = 42;

    public function doSomething(required string param1, string param2="default") {
        // function body
    }

    public string function getWhat(){
        return variables.what;
    }

    public numeric function getAge(){
        return variables.age;
    }
}