window.onload = function() {
    var trackDuckButton = document.getElementById("trackduck_button_id");
    trackDuckButton.onclick = function () {
        window.open(trackDuckButton.getAttribute('href'), '_blank');
        return false;
    };
};