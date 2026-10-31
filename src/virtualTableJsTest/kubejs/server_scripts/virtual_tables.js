// Real native JSON serializers. Reload changes one fixture so stale positive/negative bindings are observable.
ServerEvents.recipes(event => {
    const revision = 1;
    ['avaritia', 'extendedcrafting'].forEach(mod => {
        event.custom({
            type: mod + ':shaped_table', tier: 1,
            pattern: ['I I', ' G '],
            key: { I: {item: 'minecraft:iron_ingot'}, G: {item: 'minecraft:gold_ingot'} },
            result: {id: 'minecraft:amethyst_shard', count: 2}
        }).id('ae2ltpp:js_shape_' + mod);
        event.custom({
            type: mod + ':shapeless_table', tier: 1,
            ingredients: [{item: 'minecraft:copper_ingot'}, {item: 'minecraft:gold_ingot'}],
            result: {id: 'minecraft:prismarine_shard', count: 1}
        }).id('ae2ltpp:js_shapeless_' + mod);
        event.remove({id: 'ae2ltpp:js_removed_' + mod});
        event.remove({id: 'ae2ltpp:js_replaced_' + mod});
        event.custom({
            type: mod + ':shapeless_table', tier: 1,
            ingredients: [{item: 'minecraft:cobblestone'}],
            result: {id: 'minecraft:flint', count: 1}
        }).id('ae2ltpp:js_replaced_' + mod);
        if (revision === 1) {
            event.custom({
                type: mod + ':shapeless_table', tier: 1,
                ingredients: [{item: 'minecraft:clay_ball'}],
                result: {id: 'minecraft:brick', count: 1}
            }).id('ae2ltpp:js_reload_old_' + mod);
        } else {
            event.custom({
                type: mod + ':shapeless_table', tier: 1,
                ingredients: [{item: 'minecraft:clay_ball'}],
                result: {id: 'minecraft:nether_brick', count: 1}
            }).id('ae2ltpp:js_reload_new_' + mod);
        }
    });
    event.custom({
        type: 'avaritia:extreme_smithing',
        template: {item: 'minecraft:paper'}, base: {item: 'minecraft:wooden_sword'},
        addition: [{item: 'minecraft:iron_ingot'}, {item: 'minecraft:gold_ingot'}, {item: 'minecraft:copper_ingot'}],
        result: {id: 'minecraft:stone_sword', count: 1}
    }).id('ae2ltpp:js_smithing');
    console.info('PP_VIRTUAL_TABLE_JS revision=' + revision);
});
