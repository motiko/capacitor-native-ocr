import { NativeOcr } from 'capacitor-native-ocr';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    NativeOcr.echo({ value: inputValue })
}
