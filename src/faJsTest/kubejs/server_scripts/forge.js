// Same bee result, costs, tier, duplicate input and enhancers as BBB 3.07.8's
// mod/forbidden/bac.js. Vanilla substitutes avoid requiring the entire modpack.
ServerEvents.recipes(event => {
    event.recipes.forbidden_arcanus.ritual(
        RitualResults.ofCreateItemResult(Item.of('productivebees:spawn_egg_configurable_bee[entity_data={id:"productivebees:configurable_bee",type:"productivebees:butcher"}]')),
        'minecraft:bee_spawn_egg'
    )
        .addInput('minecraft:water_bucket', 1)
        .addInput('forbidden_arcanus:dark_matter', 1)
        .addInput('minecraft:rotten_flesh', 1)
        .addInput('minecraft:rotten_flesh', 1)
        .essences(100, 10, 1000, 0)
        .tier(1)
        .matchTierExact(false)
        .enhancers('forbidden_arcanus:maledictus_pact', 'forbidden_arcanus:crimson_stone')
        .magicCircles('forbidden_arcanus:upgrade_tier')
        .id('ae2ltpp:js_butcher_bee');
});
